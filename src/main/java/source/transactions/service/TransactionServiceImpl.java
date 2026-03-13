package source.transactions.service;

import org.springframework.stereotype.Service;
import source.transactions.dto.EstimatedFeeDTO;
import source.transactions.dto.TransactionRequestDTO;
import source.transactions.dto.TransactionResponseDTO;
import source.transactions.dto.UnsignedTransactionDTO;
import source.transactions.model.PendingTransaction;
import source.transactions.repository.PendingTransactionRedisRepository;
import source.transactions.dto.WithdrawRequestDTO;
import source.transactions.infra.BlockchainClient;
import source.ledger.service.LedgerService;
import source.ledger.dto.TransactionDTO;
import source.wallet.model.WalletEntity;
import source.ledger.entity.LedgerEntity;
import source.ledger.exceptions.LedgerExceptions;

import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final PendingTransactionRedisRepository pendingTxRepository;
    private final BlockchainMonitorService monitorService;
    private final source.notification.service.NotificationService notificationService;
    private final source.wallet.repository.WalletRepository walletRepository;
    private final LedgerService ledgerService;
    private final source.ledger.orchestrator.TransactionContract ledgerTransactionOrchestrator;
    private final source.ledger.repository.LedgerTransactionHistoryRepository historyRepository;
    private final source.auth.application.service.validation.totp.contratcs.TOTPVerifier totpVerifier;
    private final source.auth.application.service.webauthn.WebAuthnService webAuthnService;
    private final source.auth.application.service.user.contract.UserServiceContract userService;
    private final source.auth.application.service.cripto.contracts.Hasher hasher;
    private final source.transactions.infra.MpcSidecarClient mpcClient;
    private final source.ledger.repository.LedgerEntryRepository ledgerEntryRepository;
    private final BlockchainClient blockchainClient;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TransactionServiceImpl.class);

    @Value("${bitcoin.master-key:}")
    private String masterKey;

    private static final long AVERAGE_TX_SIZE_BYTES = 225; // Tamanho médio de uma TX

    public TransactionServiceImpl(PendingTransactionRedisRepository pendingTxRepository,
            BlockchainMonitorService monitorService,
            source.notification.service.NotificationService notificationService,
            source.wallet.repository.WalletRepository walletRepository,
            LedgerService ledgerService,
            source.ledger.orchestrator.TransactionContract ledgerTransactionOrchestrator,
            source.ledger.repository.LedgerTransactionHistoryRepository historyRepository,
            source.auth.application.service.validation.totp.contratcs.TOTPVerifier totpVerifier,
            source.auth.application.service.webauthn.WebAuthnService webAuthnService,
            source.auth.application.service.user.contract.UserServiceContract userService,
            @org.springframework.beans.factory.annotation.Qualifier("Argon2Hasher") source.auth.application.service.cripto.contracts.Hasher hasher,
            source.transactions.infra.MpcSidecarClient mpcClient,
            source.ledger.repository.LedgerEntryRepository ledgerEntryRepository,
            BlockchainClient blockchainClient) {
        this.pendingTxRepository = pendingTxRepository;
        this.monitorService = monitorService;
        this.notificationService = notificationService;
        this.walletRepository = walletRepository;
        this.ledgerService = ledgerService;
        this.ledgerTransactionOrchestrator = ledgerTransactionOrchestrator;
        this.historyRepository = historyRepository;
        this.totpVerifier = totpVerifier;
        this.webAuthnService = webAuthnService;
        this.userService = userService;
        this.hasher = hasher;
        this.mpcClient = mpcClient;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.blockchainClient = blockchainClient;
    }

    @Override
    public EstimatedFeeDTO estimateFee(BigDecimal amount) {
        // TODO: Substituir por um novo Client Blockchain. Usando hardcoded defaults por
        // enquanto.
        Long fastSatPerByte = 50L;
        Long standardSatPerByte = 35L;
        Long slowSatPerByte = 15L;

        // Calcular taxas em satoshis (tamanho da TX × taxa por byte)
        Long fastTotalSats = fastSatPerByte * AVERAGE_TX_SIZE_BYTES;
        Long standardTotalSats = standardSatPerByte * AVERAGE_TX_SIZE_BYTES;
        Long slowTotalSats = slowSatPerByte * AVERAGE_TX_SIZE_BYTES;

        // Converter para BTC
        BigDecimal fastBtc = satoshisToBtc(fastTotalSats);
        BigDecimal standardBtc = satoshisToBtc(standardTotalSats);
        BigDecimal slowBtc = satoshisToBtc(slowTotalSats);

        // Calcular quanto o destinatário receberá (usando taxa padrão como default)
        BigDecimal amountReceived = amount.subtract(standardBtc);
        BigDecimal totalToSend = amount.add(standardBtc);

        EstimatedFeeDTO estimate = new EstimatedFeeDTO(
                fastSatPerByte, standardSatPerByte, slowSatPerByte, amountReceived, totalToSend);
        estimate.setEstimatedFastBtc(fastBtc);
        estimate.setEstimatedStandardBtc(standardBtc);
        estimate.setEstimatedSlowBtc(slowBtc);

        return estimate;
    }

    @Override
    public UnsignedTransactionDTO createUnsignedTransaction(TransactionRequestDTO request) {
        // Gerar um txid temporário para rastreamento
        String tempTxId = "temp-" + UUID.randomUUID().toString();

        // Criar DTO com transação não assinada
        UnsignedTransactionDTO unsignedTx = new UnsignedTransactionDTO();
        unsignedTx.setTxId(tempTxId);
        unsignedTx.setFromAddress(request.getFromAddress());
        unsignedTx.setToAddress(request.getToAddress());
        unsignedTx.setTotalAmount(request.getAmount());
        unsignedTx.setFee(request.getFeeSatoshis());

        // Aqui você deve gerar a raw transaction hex usando bitcoinj ou similar
        // Por enquanto retornamos placeholder
        unsignedTx.setRawTxHex("RAW_TX_HEX_PLACEHOLDER");

        // Registrar no banco para monitoramento futuro (PENDING)
        try {
            source.ledger.entity.LedgerTransactionHistory history = new source.ledger.entity.LedgerTransactionHistory();
            history.setId(UUID.randomUUID());
            history.setAmount(request.getAmount());
            history.setCreatedAt(java.time.LocalDateTime.now());
            history.setContext("Unsigned Transaction created for address: " + request.getToAddress());
            history.setSenderUserId(null); // Not broadcast yet, but usually user who created it
            history.setReceiverIdentifier(request.getToAddress());
            history.setTransactionType("EXTERNAL_WITHDRAWAL");
            history.setStatus("PENDING");
            historyRepository.save(history);
        } catch (Exception e) {
            System.err.println("Failed to save unsigned transaction history: " + e.getMessage());
        }

        return unsignedTx;
    }

    @Override
    public TransactionResponseDTO getTransactionStatus(String txid) {
        // Primeiro verificar se temos no Redis
        PendingTransaction pending = monitorService.getTransaction(txid);

        if (pending != null) {
            return new TransactionResponseDTO(
                    txid,
                    pending.getStatus().toLowerCase(),
                    pending.getFeeSatoshis(),
                    pending.getAmount());
        }

        // Se não tiver no Redis, consultar blockchain diretamente
        // TODO: Consultar via novo Blockchain Client

        return new TransactionResponseDTO(txid, "confirmed", 0L);
    }

    @Override
    public void checkPendingTransactions() {
        // Delegado ao BlockchainMonitorService que roda via @Scheduled
        List<PendingTransaction> pending = pendingTxRepository.findByStatus("PENDING");
        for (PendingTransaction tx : pending) {
            monitorService.checkTransaction(tx);
        }
    }

    @Override
    public TransactionResponseDTO broadcastTransaction(String rawTxHex, String toAddress, java.math.BigDecimal amount,
            String message, Long userId) {

        // ── Broadcast via Pocket Network ──────────────────────────────────────
        // Sends the signed raw transaction hex to the Bitcoin network through
        // the Pocket Network decentralized RPC gateway.
        String txid = blockchainClient.sendRawTransaction(rawTxHex);

        if (txid == null || txid.isBlank()) {
            log.error("Pocket Network broadcast failed — rawTxHex length={}, toAddress={}, userId={}",
                    rawTxHex != null ? rawTxHex.length() : 0, toAddress, userId);
            throw new RuntimeException(
                    "Falha ao transmitir transação: Pocket Network não retornou um txid válido. Tente novamente.");
        }

        log.info("Transaction broadcast successful — txid={}, toAddress={}, amount={}, userId={}",
                txid, toAddress, amount, userId);

        // Registrar como pendente para monitoramento
        PendingTransaction pending = new PendingTransaction();
        pending.setTxid(txid);
        pending.setStatus("PENDING");
        pending.setRawTxHex(rawTxHex);
        pending.setUserId(userId);
        if (amount != null) {
            pending.setAmount(amount);
        }

        pendingTxRepository.save(pending);

        // Save explicit history for Broadcast
        try {
            source.ledger.entity.LedgerTransactionHistory history = new source.ledger.entity.LedgerTransactionHistory();
            history.setId(java.util.UUID.randomUUID());
            history.setAmount(amount != null ? amount : BigDecimal.ZERO);
            history.setCreatedAt(java.time.LocalDateTime.now());
            history.setContext("Broadcast Transaction: " + (message != null ? message : "Outgoing transacion"));
            history.setSenderUserId(userId);
            history.setReceiverIdentifier(toAddress);
            history.setBlockchainTxid(txid);
            history.setTransactionType("EXTERNAL_WITHDRAWAL");
            history.setStatus("PENDING");
            historyRepository.save(history);
        } catch (Exception e) {
            log.warn("Failed to save broadcast history for txid={}: {}", txid, e.getMessage());
        }
        try {
            String senderTitle = "Transação Transmitida";
            String senderBody = "A transação foi enviada para processamento na rede Blockchain.";
            if (amount != null) {
                senderBody = String.format("O envio de %s BTC foi transmitido com sucesso.",
                        amount.toPlainString());
            }
            notificationService.notifyUser(userId, senderTitle, senderBody);
        } catch (Exception e) {
            log.warn("Failed to notify sender for txid={}: {}", txid, e.getMessage());
        }

        // NOTIFICAÇÃO PUSH PARA O DESTINATÁRIO
        if (toAddress != null && !toAddress.isEmpty()) {
            notifyRecipient(toAddress, txid, amount, message);
        }

        return new TransactionResponseDTO(txid, "pending", 0L);
    }

    private void notifyRecipient(String address, String txid, BigDecimal amount, String userMessage) {
        try {
            source.wallet.model.WalletEntity wallet = walletRepository.findByPassphraseHash(address);
            if (wallet != null && wallet.getUser() != null) {
                Long userId = wallet.getUser().getId();

                String title = "Recurso Recebido";
                String body = "Uma nova transferência foi identificada em sua carteira.";

                if (amount != null) {
                    body = String.format("Aporte de %s BTC identificado na carteira '%s'.",
                            amount.toPlainString(), wallet.getName());
                }

                if (userMessage != null && !userMessage.isEmpty()) {
                    body += " Mensagem: " + userMessage;
                }

                notificationService.notifyUser(userId, title, body);
            }
        } catch (Exception e) {
            // Non-blocking notification failure
            System.err.println("Failed to notify recipient: " + e.getMessage());
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public TransactionResponseDTO withdraw(Long userId, WithdrawRequestDTO request) {
        System.out.println("🏦 [WITHDRAW] Processando saque para usuário " + userId);

        // 1. Validação Robusta de Dados
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Valor de saque deve ser positivo.");
        }

        if (!isValidBitcoinAddress(request.getToAddress())) {
            throw new RuntimeException("Endereço Bitcoin inválido: " + request.getToAddress());
        }

        // 2. Localizar Wallet e Validar Propriedade
        WalletEntity wallet = walletRepository.findByName(request.getFromWalletName());
        if (wallet == null || !wallet.getUser().getId().equals(userId)) {
            throw new RuntimeException("Carteira de origem não encontrada ou não pertence a você.");
        }

        source.auth.model.entity.UserDataBase user = userService.buscarPorId(userId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado."));

        // 3. Validar TOTP Exclusivo da Carteira (Sempre exigido agora por segurança
        // extra)
        if (request.getTotpCode() == null || request.getTotpCode().isBlank()) {
            throw new source.auth.AuthExceptions.IncorrectTotpException("TOTP code is required for withdrawal.");
        }
        if (!totpVerifier.totpMatcher(wallet.getTotpSecret(), request.getTotpCode())) {
            throw new source.auth.AuthExceptions.IncorrectTotpException("Invalid Wallet TOTP code.");
        }

        // 4. Validar Passkey se habilitado para transações
        if (user.getPasskeyEnabledForTransactions()) {
            if (request.getPasskeyAssertionResponseJSON() == null
                    || request.getPasskeyAssertionResponseJSON().isBlank()) {
                // Se não enviou a resposta, geramos o desafio (Assertion Request)
                String challenge = webAuthnService.startLogin(user.getUsername());
                throw new source.auth.AuthExceptions.AuthValidationException("PASSKEY_CHALLENGE_REQUIRED:" + challenge);
            }

            // Validar a assinatura da Passkey
            boolean passkeyValid = webAuthnService.finishLogin(
                    request.getPasskeyAssertionRequestJSON(),
                    request.getPasskeyAssertionResponseJSON());

            if (!passkeyValid) {
                throw new source.auth.AuthExceptions.AuthValidationException(
                        "Invalid Passkey signature for transaction.");
            }
        }

        // 5. Autenticação Adicional baseada no Tipo de Conta (Multi-Sig / Shamir)
        String platformSignature = "";
        if (user.getAccountSecurity().equals(source.auth.model.enums.AccountSecurityType.MULTISIG_2FA) ||
                user.getAccountSecurity().equals(source.auth.model.enums.AccountSecurityType.SHAMIR)) {

            if (request.getConfirmationPassphrase() == null || request.getConfirmationPassphrase().isEmpty()) {
                throw new source.auth.AuthExceptions.InvalidCredentials(
                        "A passphrase é obrigatória para assinar esta transação.");
            }

            // GATILHO: Validação Argon2id antes de liberar o Sidecar MPC
            if (!hasher.verify(request.getConfirmationPassphrase().toCharArray(), user.getPassphrase())) {
                throw new source.auth.AuthExceptions.InvalidPassphrase("Passphrase inválida para autorização MPC.");
            }

            // Com a senha criptograficamente aceita, liberamos o uso do estilhaço MPC.
            byte[] messageHash = new byte[32]; // Placeholder para o hash da TX
            byte[] mpcSignature = mpcClient.sign(user.getUsername(), messageHash, "TARGET_PUBKEY");

            platformSignature = "_MPC_SIGNED_"
                    + java.util.Base64.getEncoder().encodeToString(mpcSignature).substring(0, 10);
        }

        // 6. Estimar Taxas da Rede (para deduzir ou informar o usuário)
        EstimatedFeeDTO fees = estimateFee(request.getAmount());
        BigDecimal networkFee = fees.getEstimatedStandardBtc();

        // CÁLCULO DE FEE DA PLATAFORMA (ex: 1% sob o total)
        BigDecimal platformFee = request.getAmount().multiply(new BigDecimal("0.01")).setScale(8, RoundingMode.HALF_UP);
        BigDecimal totalToDebit = request.getAmount().add(networkFee).add(platformFee);

        // 7. Verificar Saldo no Ledger Interno
        LedgerEntity ledger = ledgerService.findByWalletId(wallet.getId());
        if (ledger.getBalance().compareTo(totalToDebit) < 0) {
            throw new LedgerExceptions.InsufficientBalanceException(
                    "Saldo insuficiente para cobrir o saque e as taxas de rede.");
        }

        // 8. Débito no Ledger Interno
        TransactionDTO ledgerTx = new TransactionDTO();
        ledgerTx.setSender(request.getFromWalletName());
        ledgerTx.setReceiver("SYSTEM_WITHDRAWAL_VAULT");
        ledgerTx.setAmount(totalToDebit);
        ledgerTx.setContext("WITHDRAWAL_" + request.getToAddress());
        ledgerTx.setConfirmationPassphrase(request.getConfirmationPassphrase());
        ledgerTx.setTotpCode(request.getTotpCode());
        ledgerTx.setPasskeyAssertionJson(request.getPasskeyAssertionResponseJSON());

        ledgerTransactionOrchestrator.processTransaction(ledgerTx);

        // GRAVA O LEDGER DE AUDITORIA (Separação Cirúrgica)
        // amount_net = -(Saque_Real_Com_Miners + Fee_Plataforma) para bater balança
        // perfeitamente
        // fee_amount = Fee_Plataforma (Profit que foi subtraído da Liability)
        source.ledger.entity.LedgerEntry entry = new source.ledger.entity.LedgerEntry(
                UUID.randomUUID(),
                String.valueOf(userId),
                totalToDebit.negate(),
                platformFee,
                "PENDING");
        ledgerEntryRepository.save(entry);

        // 9. Criar, Assinar e Broadcast da Transação On-Chain
        String dummySignedHex = "0100000001" + UUID.randomUUID().toString().replace("-", "").substring(0, 64)
                + "0000000000" + platformSignature;

        TransactionResponseDTO response = broadcastTransaction(
                dummySignedHex,
                request.getToAddress(),
                request.getAmount(),
                "Saque On-Chain: " + request.getDescription(),
                userId);

        // Save explicit history for Withdrawal
        source.ledger.entity.LedgerTransactionHistory history = new source.ledger.entity.LedgerTransactionHistory();
        history.setId(java.util.UUID.randomUUID());
        history.setAmount(totalToDebit);
        history.setCreatedAt(java.time.LocalDateTime.now());
        history.setContext("On-Chain Withdrawal: " + request.getDescription());
        history.setSenderUserId(userId);
        history.setSenderIdentifier(request.getFromWalletName());
        history.setReceiverIdentifier(request.getToAddress());
        history.setBlockchainTxid(response.getTxid());
        history.setTransactionType("EXTERNAL_WITHDRAWAL");
        history.setStatus("PENDING");
        historyRepository.save(history);

        System.out.println("✅ [WITHDRAW] Saque transmitido: " + response.getTxid());
        return response;
    }

    private boolean isValidBitcoinAddress(String address) {
        if (address == null || address.isEmpty())
            return false;
        // Basic Regex for BTC addresses (Mainnet: 1, 3, bc1)
        return address.matches("^(1|3|bc1)[a-zA-Z0-9]{25,62}$");
    }

    private BigDecimal satoshisToBtc(Long satoshis) {
        return BigDecimal.valueOf(satoshis).divide(
                BigDecimal.valueOf(100_000_000), 8, RoundingMode.HALF_UP);
    }
}
