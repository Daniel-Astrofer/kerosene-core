package source.ledger.orchestrator;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.service.user.UserService;
import source.auth.model.entity.UserDataBase;
import source.ledger.dto.TransactionDTO;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.service.LedgerContract;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletContract;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Orquestrador de transações entre carteiras.
 *
 * Suporta múltiplos formatos de identificação:
 * - Username: "alice", "bob"
 * - Wallet ID: "1", "2", etc.
 * - Bitcoin Address: "1A1z7agoat7F9gq5...", "3J98t1W1mU4..."
 *
 * Proteções de segurança:
 * ① Idempotência via Redis — deduplicação por idempotencyKey (anti
 * double-spend).
 * ② Timestamp — rejeição de requisições com mais de MAX_REQUEST_AGE_MS ms (anti
 * replay).
 * ③ Verificação de saldo DENTRO do lock pessimista do PostgreSQL — elimina
 * a race condition entre SELECT balance e UPDATE balance.
 */
@Component
public class Transaction implements TransactionContract {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Transaction.class);

    /**
     * Janela de validade da requisição: 2 minutos.
     * Qualquer requisição com timestamp mais antigo é rejeitada como possível
     * replay.
     */
    private static final long MAX_REQUEST_AGE_MS = 2 * 60 * 1_000L;

    /**
     * TTL da chave de idempotência no Redis.
     * Deve ser maior que MAX_REQUEST_AGE_MS para garantir que todas as
     * requisições válidas (até 2 min antigas) sejam deduplicadas corretamente.
     */
    private static final long IDEMPOTENCY_TTL_MINUTES = 10L;

    private static final String IDEMPOTENCY_PREFIX = "tx_idem:";

    private final WalletContract walletService;
    private final LedgerContract ledgerService;
    private final UserService userService;
    private final source.notification.service.NotificationService notificationService;
    private final source.ledger.repository.LedgerTransactionHistoryRepository historyRepository;
    private final StringRedisTemplate redisTemplate;
    private final source.auth.application.service.webauthn.WebAuthnService webAuthnService;
    private final source.auth.application.service.validation.totp.contratcs.TOTPVerifier totpVerifier;
    private final source.auth.application.service.cripto.contracts.Hasher hasher;

    public Transaction(WalletContract walletContract, LedgerContract ledgerContract, UserService userService,
            source.notification.service.NotificationService notificationService,
            source.ledger.repository.LedgerTransactionHistoryRepository historyRepository,
            StringRedisTemplate redisTemplate,
            source.auth.application.service.webauthn.WebAuthnService webAuthnService,
            source.auth.application.service.validation.totp.contratcs.TOTPVerifier totpVerifier,
            @org.springframework.beans.factory.annotation.Qualifier("Argon2Hasher") source.auth.application.service.cripto.contracts.Hasher hasher) {
        this.walletService = walletContract;
        this.ledgerService = ledgerContract;
        this.userService = userService;
        this.notificationService = notificationService;
        this.historyRepository = historyRepository;
        this.redisTemplate = redisTemplate;
        this.webAuthnService = webAuthnService;
        this.totpVerifier = totpVerifier;
        this.hasher = hasher;
    }

    @Override
    @Transactional
    public void processTransaction(TransactionDTO dto) {

        // ── ① Anti-Replay: Timestamp Validation ────────────────────────────
        // Reject requests whose timestamp is older than MAX_REQUEST_AGE_MS.
        // This prevents a captured packet from being replayed after a delay.
        if (dto.getRequestTimestamp() != null) {
            long age = System.currentTimeMillis() - dto.getRequestTimestamp();
            if (age > MAX_REQUEST_AGE_MS || age < -30_000L) {
                // Also reject timestamps far in the future (clock skew attack).
                log.warn("TX REJECTED — timestamp out of window: age={}ms, key={}", age, dto.getIdempotencyKey());
                throw new LedgerExceptions.TransactionReplayException(
                        "Request timestamp is out of the allowed window. Please retry with a fresh request.");
            }
        }

        // ── ② Idempotency Check via Redis ───────────────────────────────────
        // If the app sends the same idempotencyKey twice (user double-tapped),
        // we detect it here and discard the duplicate BEFORE touching the DB.
        if (dto.getIdempotencyKey() != null && !dto.getIdempotencyKey().isBlank()) {
            String redisKey = IDEMPOTENCY_PREFIX + dto.getIdempotencyKey();
            // setIfAbsent returns true only when the key did NOT exist → first request
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, "processing", IDEMPOTENCY_TTL_MINUTES, TimeUnit.MINUTES);

            if (Boolean.FALSE.equals(isNew)) {
                // Key already existed → duplicate request. Safe to discard.
                log.warn("TX REJECTED — duplicate idempotencyKey={}", dto.getIdempotencyKey());
                throw new LedgerExceptions.DuplicateTransactionException(
                        "This transaction has already been submitted (duplicate idempotency key). "
                                + "If the previous attempt failed, generate a new idempotency key and retry.");
            }
            // Key marked as "processing". Will be kept in Redis for
            // IDEMPOTENCY_TTL_MINUTES.
        } else {
            // No idempotency key provided — log a warning but allow (backwards compat).
            log.warn("TX WARNING — no idempotencyKey provided. double-send protection disabled for this request.");
        }

        // ── ③ Resolve & Authenticate Sender ────────────────────────────────
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long senderUserId = Long.parseLong(auth.getName());

        UserDataBase sender = userService.buscarPorId(senderUserId).orElseThrow(
                () -> new LedgerExceptions.LedgerNotFoundException("Sender (authenticated user) not found"));

        // Verify that the user has provided the required confirmation
        // (Passkey/Passphrase/TOTP)
        verifyTransactionAuthentication(sender, dto);

        // ── ④ Resolve wallets ──────────────────────────────────────────────
        WalletEntity senderWallet = resolveSenderWallet(sender, dto.getSender());
        WalletEntity receiverWallet = resolveReceiverWallet(dto.getReceiver());

        // ── ④ Balance check is deferred to LedgerService.updateBalance() ───
        // LedgerService.updateBalance() uses SELECT FOR UPDATE (pessimistic lock)
        // so the balance is verified inside the DB lock — no race condition possible.
        // A pre-check here would be a TOCTOU (Time-Of-Check-Time-Of-Use) bug.

        String sharedContext = String.format("Transfer from @%s to @%s", sender.getUsername(),
                receiverWallet.getUser().getUsername());
        if (dto.getContext() != null && !dto.getContext().trim().isEmpty()) {
            sharedContext = dto.getContext();
        }

        // Credit receiver first (can't over-credit; only over-debit is dangerous)
        ledgerService.updateBalance(receiverWallet.getId(), dto.getAmount(), sharedContext);

        // Debit sender (LedgerService checks balance under lock here)
        BigDecimal debitAmount = dto.getAmount().negate();
        ledgerService.updateBalance(senderWallet.getId(), debitAmount, sharedContext);

        // ── ⑤ History records ──────────────────────────────────────────────
        try {
            source.ledger.entity.LedgerTransactionHistory senderHistory = new source.ledger.entity.LedgerTransactionHistory();
            senderHistory.setId(java.util.UUID.randomUUID());
            senderHistory.setAmount(dto.getAmount().abs());
            senderHistory.setCreatedAt(java.time.LocalDateTime.now());
            senderHistory.setContext(sharedContext);
            senderHistory.setSenderUserId(sender.getId());
            senderHistory.setSenderIdentifier(senderWallet.getName());
            senderHistory.setReceiverUserId(receiverWallet.getUser().getId());
            senderHistory.setReceiverIdentifier(receiverWallet.getName());
            senderHistory.setTransactionType("INTERNAL");
            senderHistory.setStatus("CONCLUDED");
            historyRepository.save(senderHistory);

            source.ledger.entity.LedgerTransactionHistory receiverHistory = new source.ledger.entity.LedgerTransactionHistory();
            receiverHistory.setId(java.util.UUID.randomUUID());
            receiverHistory.setAmount(dto.getAmount().abs());
            receiverHistory.setCreatedAt(java.time.LocalDateTime.now());
            receiverHistory.setContext(sharedContext);
            receiverHistory.setSenderUserId(sender.getId());
            receiverHistory.setSenderIdentifier(senderWallet.getName());
            receiverHistory.setReceiverUserId(receiverWallet.getUser().getId());
            receiverHistory.setReceiverIdentifier(receiverWallet.getName());
            receiverHistory.setTransactionType("INTERNAL");
            receiverHistory.setStatus("CONCLUDED");
            historyRepository.save(receiverHistory);
        } catch (Exception e) {
            log.error("Failed to save split transaction history: {}", e.getMessage());
        }

        // ── ⑥ Push Notifications ───────────────────────────────────────────
        try {
            notificationService.notifyUser(receiverWallet.getUser().getId(), "Transferência Recebida",
                    String.format("Aporte de %s BTC recebido de @%s para a carteira '%s'.",
                            dto.getAmount().toPlainString(), sender.getUsername(), receiverWallet.getName()));
            notificationService.notifyUser(sender.getId(), "Transferência Enviada",
                    String.format("Envio de %s BTC realizado para @%s a partir da carteira '%s'.",
                            dto.getAmount().toPlainString(), receiverWallet.getUser().getUsername(),
                            senderWallet.getName()));
        } catch (Exception e) {
            log.warn("Notification failed (non-blocking): {}", e.getMessage());
        }
    }

    private WalletEntity resolveSenderWallet(UserDataBase sender, String senderIdentifier) {
        List<WalletEntity> senderWallets = walletService.findByUserId(sender.getId());
        if (senderWallets == null || senderWallets.isEmpty()) {
            throw new LedgerExceptions.LedgerNotFoundException("Sender wallet not found");
        }

        if (senderIdentifier == null || senderIdentifier.trim().isEmpty()) {
            return senderWallets.get(0);
        }

        if (TransactionDTO.isNumericId(senderIdentifier)) {
            long walletId = Long.parseLong(senderIdentifier);
            return senderWallets.stream()
                    .filter(w -> w.getId() == walletId)
                    .findFirst()
                    .orElseThrow(() -> new LedgerExceptions.LedgerNotFoundException(
                            "Sender wallet with ID " + walletId + " not found"));
        }

        if (TransactionDTO.isBitcoinAddress(senderIdentifier) || isHashFormat(senderIdentifier)) {
            WalletEntity walletByAddress = walletService.findByPassphraseHash(senderIdentifier);
            if (walletByAddress != null && walletByAddress.getUser().getId().equals(sender.getId())) {
                return walletByAddress;
            }
            return senderWallets.stream()
                    .filter(w -> w.getPassphraseHash().equals(senderIdentifier))
                    .findFirst()
                    .orElseThrow(() -> new LedgerExceptions.LedgerNotFoundException(
                            "Sender wallet with address '" + senderIdentifier + "' not found"));
        }

        String senderIdentifierUpperCase = senderIdentifier.toUpperCase();
        return senderWallets.stream()
                .filter(w -> w.getName().equals(senderIdentifierUpperCase))
                .findFirst()
                .orElseThrow(() -> new LedgerExceptions.LedgerNotFoundException(
                        "Sender wallet with name '" + senderIdentifier + "' not found"));
    }

    private WalletEntity resolveReceiverWallet(String receiverIdentifier) {
        if (receiverIdentifier == null || receiverIdentifier.trim().isEmpty()) {
            throw new LedgerExceptions.ReceiverNotFoundException("Receiver identifier cannot be empty");
        }

        if (TransactionDTO.isNumericId(receiverIdentifier)) {
            try {
                long walletId = Long.parseLong(receiverIdentifier);
                WalletEntity wallet = walletService.findById(walletId);
                if (wallet != null)
                    return wallet;
            } catch (NumberFormatException ignored) {
            }
        }

        if (TransactionDTO.isBitcoinAddress(receiverIdentifier) || isHashFormat(receiverIdentifier)) {
            WalletEntity wallet = walletService.findByPassphraseHash(receiverIdentifier);
            if (wallet != null)
                return wallet;
            throw new LedgerExceptions.ReceiverNotFoundException(
                    "Receiver wallet with address '" + receiverIdentifier + "' not found");
        }

        UserDataBase receiver = userService.findByUsername(receiverIdentifier);
        if (receiver == null) {
            throw new LedgerExceptions.ReceiverNotFoundException(
                    "Receiver username '" + receiverIdentifier + "' not found");
        }

        List<WalletEntity> receiverWallets = walletService.findByUserId(receiver.getId());
        if (receiverWallets == null || receiverWallets.isEmpty()) {
            throw new LedgerExceptions.ReceiverNotFoundException(
                    "Receiver wallet not found for user '" + receiverIdentifier + "'");
        }

        return receiverWallets.get(0);
    }

    private boolean isHashFormat(String identifier) {
        if (identifier == null || identifier.trim().isEmpty())
            return false;
        return identifier.matches("^[A-Za-z0-9+/]+=*$") && identifier.contains("=");
    }

    /**
     * Verifies that the sender has provided the necessary cryptographic or 2FA
     * proof for the transaction, based on their account security level and
     * settings.
     */
    private void verifyTransactionAuthentication(UserDataBase sender, TransactionDTO dto) {
        log.info("Verifying transaction auth for user: {} (Security: {})", sender.getUsername(),
                sender.getAccountSecurity());

        // 1. Check Passkey (FIDO2) - if enabled, it's the strongest factor
        if (Boolean.TRUE.equals(sender.getPasskeyEnabledForTransactions())) {
            if (dto.getPasskeyAssertionJson() == null || dto.getPasskeyAssertionJson().isEmpty()) {
                throw new source.auth.AuthExceptions.InvalidCredentials(
                        "Passkey authentication is required for transactions on this account.");
            }

            // We need the original challenge to verify. We assume the app fetched one
            // and it's stored in Redis under "passkey_challenge:{username}"
            String challengeKey = "passkey_challenge:" + sender.getUsername();
            String assertionRequestJson = redisTemplate.opsForValue().get(challengeKey);

            if (assertionRequestJson == null) {
                throw new source.auth.AuthExceptions.InvalidCredentials(
                        "Passkey challenge expired or not found. Please request a new challenge before signing.");
            }

            if (!webAuthnService.finishLogin(assertionRequestJson, dto.getPasskeyAssertionJson())) {
                throw new source.auth.AuthExceptions.InvalidCredentials("Invalid Passkey signature for transaction.");
            }

            // Consume challenge
            redisTemplate.delete(challengeKey);
            log.info("Passkey transaction auth verified for {}", sender.getUsername());
            return; // Passkey is sufficient
        }

        // 2. Check Multisig/Shamir Passphrase Confirmation
        if (sender.getAccountSecurity() == source.auth.model.enums.AccountSecurityType.MULTISIG_2FA ||
                sender.getAccountSecurity() == source.auth.model.enums.AccountSecurityType.SHAMIR) {

            if (dto.getConfirmationPassphrase() == null || dto.getConfirmationPassphrase().isEmpty()) {
                throw new source.auth.AuthExceptions.InvalidCredentials(
                        "This account requires passphrase confirmation for all transactions.");
            }

            if (!hasher.verify(dto.getConfirmationPassphrase().toCharArray(), sender.getPassphrase())) {
                throw new source.auth.AuthExceptions.InvalidPassphrase(
                        "Invalid passphrase for transaction authorization.");
            }

            log.info("Mnemonic/Passphrase transaction auth verified for {}", sender.getUsername());
        }

        // 3. Optional/Fallback: TOTP Verification
        // If TOTP is provided, we verify it. For some security levels, it might be
        // explicitly required.
        if (dto.getTotpCode() != null && !dto.getTotpCode().isEmpty()) {
            if (sender.getTOTPSecret() == null) {
                throw new source.auth.AuthExceptions.IncorrectTotpException("TOTP not configured for this account.");
            }
            totpVerifier.totpVerify(sender.getTOTPSecret(), dto.getTotpCode());
            log.info("TOTP transaction auth verified for {}", sender.getUsername());
        }
    }
}
