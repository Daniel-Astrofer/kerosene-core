package source.bitcoinaccounts.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.model.BitcoinAccountEntity;
import source.bitcoinaccounts.model.InternalBtcCardEntity;
import source.bitcoinaccounts.model.LedgerEntryEntity;
import source.bitcoinaccounts.model.ReceivingAddressEntity;
import source.bitcoinaccounts.model.ReceivingRequestEntity;
import source.bitcoinaccounts.repository.BitcoinAccountRepository;
import source.bitcoinaccounts.repository.InternalBtcCardRepository;
import source.bitcoinaccounts.repository.ReceivingAddressRepository;
import source.bitcoinaccounts.repository.ReceivingRequestRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@Service
public class ReceivingRequestService {

    private final ReceivingRequestRepository requestRepository;
    private final ReceivingAddressRepository addressRepository;
    private final InternalBtcCardRepository cardRepository;
    private final BitcoinAccountRepository accountRepository;
    private final BitcoinAccountService accountService;
    private final BitcoinReceiveAddressIssuer addressIssuer;
    private final BitcoinAccountLedgerService ledgerService;
    private final BitcoinTaxEventService taxEventService;
    private final BitcoinAccountAuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String bitcoinNetwork;
    private final int minimumConfirmations;
    private final long readableRetentionHours;

    public ReceivingRequestService(
            ReceivingRequestRepository requestRepository,
            ReceivingAddressRepository addressRepository,
            InternalBtcCardRepository cardRepository,
            BitcoinAccountRepository accountRepository,
            BitcoinAccountService accountService,
            BitcoinReceiveAddressIssuer addressIssuer,
            BitcoinAccountLedgerService ledgerService,
            BitcoinTaxEventService taxEventService,
            BitcoinAccountAuditService auditService,
            @Value("${bitcoin.network:mainnet}") String bitcoinNetwork,
            @Value("${bitcoin.min-confirmations:3}") int minimumConfirmations,
            @Value("${bitcoin-accounts.readable-retention-hours:24}") long readableRetentionHours) {
        this.requestRepository = requestRepository;
        this.addressRepository = addressRepository;
        this.cardRepository = cardRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.addressIssuer = addressIssuer;
        this.ledgerService = ledgerService;
        this.taxEventService = taxEventService;
        this.auditService = auditService;
        this.bitcoinNetwork = bitcoinNetwork != null ? bitcoinNetwork : "mainnet";
        this.minimumConfirmations = Math.max(1, minimumConfirmations);
        this.readableRetentionHours = Math.max(1L, readableRetentionHours);
    }

    @Transactional
    public Map<String, Object> create(Long userId, UUID accountId, Long amountSats, String expiry, boolean oneTime) {
        InternalBtcCardEntity card = accountService.requireInternalCard(userId, accountId);
        if (card.getStatus() != BitcoinAccountEnums.CardStatus.ACTIVE) {
            throw new IllegalArgumentException("Este cartão BTC não está ativo para novos recebimentos.");
        }
        if (amountSats != null && amountSats <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }

        BitcoinReceiveAddressIssuer.IssuedAddress issued =
                addressIssuer.issue("btc-card:" + card.getId());
        ReceivingAddressEntity address = new ReceivingAddressEntity();
        address.setCardId(card.getId());
        address.setAddress(issued.address());
        address.setDerivationPath(issued.derivationPath());
        address.setDerivationIndex(issued.derivationIndex());
        address.setStatus(BitcoinAccountEnums.ReceivingAddressStatus.ASSIGNED);
        address = addressRepository.save(address);

        ReceivingRequestEntity request = new ReceivingRequestEntity();
        request.setCardId(card.getId());
        request.setAddressId(address.getId());
        request.setPublicCode(generatePublicCode());
        request.setAmountSats(amountSats);
        request.setExpiresAt(resolveExpiry(expiry));
        request.setOneTime(oneTime);
        request.setPurgeAfter(LocalDateTime.now().plusHours(readableRetentionHours));
        request = requestRepository.save(request);

        auditService.recordUser(userId, "RECEIVE_REQUEST_CREATED", "RECEIVING_REQUEST",
                request.getId().toString(), Map.of("cardId", card.getId().toString(), "oneTime", oneTime));
        return toOwnerView(request, address, accountId);
    }

    @Transactional
    public List<Map<String, Object>> listForAccount(Long userId, UUID accountId) {
        InternalBtcCardEntity card = accountService.requireInternalCard(userId, accountId);
        List<ReceivingRequestEntity> expiredRequests = new ArrayList<>();
        List<Map<String, Object>> views = new ArrayList<>();

        for (ReceivingRequestEntity request : requestRepository
                .findTop50ByCardIdAndStatusNotOrderByCreatedAtDesc(
                        card.getId(), BitcoinAccountEnums.ReceivingRequestStatus.HIDDEN)) {
            if (request.getStatus() == BitcoinAccountEnums.ReceivingRequestStatus.ACTIVE && isExpired(request)) {
                request.setStatus(BitcoinAccountEnums.ReceivingRequestStatus.EXPIRED);
                expiredRequests.add(request);
            }
            ReceivingAddressEntity address = addressRepository.findById(request.getAddressId())
                    .orElseThrow(() -> new IllegalArgumentException("Receiving address not found."));
            views.add(toOwnerView(request, address, accountId));
        }

        if (!expiredRequests.isEmpty()) {
            requestRepository.saveAll(expiredRequests);
        }
        return views;
    }

    @Transactional
    public Map<String, Object> publicView(String publicCode) {
        ReceivingRequestEntity request = requestRepository.findByPublicCode(publicCode)
                .orElseThrow(() -> new IllegalArgumentException("Este link de recebimento não existe ou expirou."));
        updateExpiryState(request);
        ReceivingAddressEntity address = addressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new IllegalArgumentException("Receiving address not found."));
        return toView(request, address, false);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ownerStatus(Long userId, UUID requestId) {
        ReceivingRequestEntity request = requireOwnedRequest(userId, requestId);
        ReceivingAddressEntity address = addressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new IllegalArgumentException("Receiving address not found."));
        return toView(request, address, true);
    }

    @Transactional
    public Map<String, Object> expire(Long userId, UUID requestId) {
        ReceivingRequestEntity request = requireOwnedRequest(userId, requestId);
        if (request.getStatus() == BitcoinAccountEnums.ReceivingRequestStatus.ACTIVE) {
            request.setStatus(BitcoinAccountEnums.ReceivingRequestStatus.EXPIRED);
            request.setExpiresAt(LocalDateTime.now());
            requestRepository.save(request);
        }
        ReceivingAddressEntity address = addressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new IllegalArgumentException("Receiving address not found."));
        address.setStatus(BitcoinAccountEnums.ReceivingAddressStatus.EXPIRED);
        addressRepository.save(address);
        return toView(request, address, true);
    }

    @Transactional
    public Map<String, Object> hide(Long userId, UUID requestId) {
        ReceivingRequestEntity request = requireOwnedRequest(userId, requestId);
        request.setStatus(BitcoinAccountEnums.ReceivingRequestStatus.HIDDEN);
        requestRepository.save(request);
        ReceivingAddressEntity address = addressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new IllegalArgumentException("Receiving address not found."));
        return toView(request, address, true);
    }

    @Transactional
    public Map<String, Object> userAction(Long userId, UUID requestId, String action) {
        ReceivingRequestEntity request = requireOwnedRequest(userId, requestId);
        if (request.getStatus() != BitcoinAccountEnums.ReceivingRequestStatus.USER_ACTION_REQUIRED
                && request.getStatus() != BitcoinAccountEnums.ReceivingRequestStatus.EXPIRED_RECEIVED) {
            return ownerStatus(userId, requestId);
        }
        if ("CONFIRM_RECOGNIZED_PAYMENT".equalsIgnoreCase(action)) {
            request.setStatus(BitcoinAccountEnums.ReceivingRequestStatus.AUTO_RESOLUTION_PENDING);
            request.setSelfServiceReason("User confirmed the late payment as recognized.");
            requestRepository.save(request);
            auditService.recordUser(userId, "RECEIVE_REQUEST_USER_CONFIRMED", "RECEIVING_REQUEST",
                    request.getId().toString(), Map.of("action", action));
            return ownerStatus(userId, requestId);
        }
        throw new IllegalArgumentException("A ação solicitada não está disponível para este recebimento.");
    }

    @Transactional
    public void observeOnchainPayment(String addressValue, String txid, int vout, long amountSats, int confirmations) {
        ReceivingAddressEntity address = addressRepository.findByAddress(addressValue)
                .orElse(null);
        if (address == null) {
            return;
        }
        ReceivingRequestEntity request = requestRepository.findTopByAddressIdOrderByCreatedAtDesc(address.getId())
                .orElse(null);
        if (request == null) {
            return;
        }

        InternalBtcCardEntity card = cardRepository.findById(request.getCardId())
                .orElseThrow(() -> new IllegalArgumentException("Internal BTC Card not found."));
        BitcoinAccountEntity account = accountRepository.findById(card.getBitcoinAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Bitcoin account not found."));

        address.setFirstSeenTxid(address.getFirstSeenTxid() == null ? txid : address.getFirstSeenTxid());
        address.setLastSeenAt(LocalDateTime.now());
        address.setStatus(isExpired(request)
                ? BitcoinAccountEnums.ReceivingAddressStatus.EXPIRED_RECEIVED
                : BitcoinAccountEnums.ReceivingAddressStatus.OBSERVED);
        addressRepository.save(address);

        String idempotencyKey = txid + ":" + vout;
        if (request.getStatus() == BitcoinAccountEnums.ReceivingRequestStatus.PAID
                && confirmations < minimumConfirmations) {
            request.setStatus(BitcoinAccountEnums.ReceivingRequestStatus.AUTO_RESOLUTION_PENDING);
            request.setSelfServiceReason("Confirmation regression detected; waiting for automatic network resolution.");
            requestRepository.save(request);
            ledgerService.moveAvailableToAutoHoldByIdempotencyKey(idempotencyKey, "CONFIRMATION_REGRESSION");
            return;
        }

        boolean expired = isExpired(request);
        boolean selfServiceApproved = request.getStatus()
                == BitcoinAccountEnums.ReceivingRequestStatus.AUTO_RESOLUTION_PENDING;
        boolean alreadyRecorded = ledgerService.hasEntryForIdempotencyKey(idempotencyKey);
        if (requiresSelfServiceHold(request, expired, selfServiceApproved, amountSats, alreadyRecorded)) {
            request.setStatus(BitcoinAccountEnums.ReceivingRequestStatus.USER_ACTION_REQUIRED);
            request.setSelfServiceReason(selfServiceHoldReason(request, expired, amountSats));
            requestRepository.save(request);
            holdObservedPayment(
                    account,
                    card,
                    request,
                    amountSats,
                    idempotencyKey,
                    "RECEIVE_USER_ACTION_REQUIRED");
            return;
        }

        request.setStatus(resolveObservedStatus(expired, selfServiceApproved, confirmations));
        if (confirmations >= minimumConfirmations) {
            request.setPaidAt(LocalDateTime.now());
        }
        requestRepository.save(request);

        LedgerEntryEntity entry = ledgerService.creditPending(
                card.getLedgerAccountId(),
                amountSats,
                "ONCHAIN_RECEIVE",
                request.getId().toString(),
                idempotencyKey);
        if (confirmations >= minimumConfirmations) {
            ledgerService.makeAvailable(entry.getId());
        }
        taxEventService.recordTemporaryEvent(
                account.getUserId(),
                BitcoinAccountEnums.TaxEventType.DEPOSIT_INTERNAL,
                amountSats,
                txid + ":" + vout,
                account.getId(),
                card.getId(),
                null,
                "USER_CLASSIFICATION_PENDING");
    }

    private boolean requiresSelfServiceHold(
            ReceivingRequestEntity request,
            boolean expired,
            boolean selfServiceApproved,
            long amountSats,
            boolean alreadyRecorded) {
        if (selfServiceApproved) {
            return false;
        }
        if (request.getStatus() == BitcoinAccountEnums.ReceivingRequestStatus.USER_ACTION_REQUIRED) {
            return true;
        }
        if (expired && !safeLatePayment(request, amountSats)) {
            return true;
        }
        return request.isOneTime()
                && request.getStatus() == BitcoinAccountEnums.ReceivingRequestStatus.PAID
                && !alreadyRecorded;
    }

    private String selfServiceHoldReason(ReceivingRequestEntity request, boolean expired, long amountSats) {
        if (request.getStatus() == BitcoinAccountEnums.ReceivingRequestStatus.USER_ACTION_REQUIRED
                && request.getSelfServiceReason() != null
                && !request.getSelfServiceReason().isBlank()) {
            return request.getSelfServiceReason();
        }
        if (expired && !safeLatePayment(request, amountSats)) {
            return "Late payment amount differs from the original receive request and needs self-service confirmation.";
        }
        if (request.isOneTime() && request.getStatus() == BitcoinAccountEnums.ReceivingRequestStatus.PAID) {
            return "Additional payment arrived on a one-time receive link and needs self-service confirmation.";
        }
        return "Payment needs self-service confirmation before automatic release.";
    }

    private void holdObservedPayment(
            BitcoinAccountEntity account,
            InternalBtcCardEntity card,
            ReceivingRequestEntity request,
            long amountSats,
            String idempotencyKey,
            String reason) {
        ledgerService.creditPending(
                card.getLedgerAccountId(),
                amountSats,
                "ONCHAIN_RECEIVE",
                request.getId().toString(),
                idempotencyKey);
        ledgerService.moveAvailableToAutoHoldByIdempotencyKey(idempotencyKey, reason);
        taxEventService.recordTemporaryEvent(
                account.getUserId(),
                BitcoinAccountEnums.TaxEventType.DEPOSIT_INTERNAL,
                amountSats,
                idempotencyKey,
                account.getId(),
                card.getId(),
                null,
                "USER_ACTION_REQUIRED");
        auditService.recordUser(account.getUserId(), "RECEIVE_PAYMENT_AUTO_HOLD", "RECEIVING_REQUEST",
                request.getId().toString(), Map.of("reason", reason));
    }

    private BitcoinAccountEnums.ReceivingRequestStatus resolveObservedStatus(
            boolean expired,
            boolean selfServiceApproved,
            int confirmations) {
        if (confirmations >= minimumConfirmations) {
            return BitcoinAccountEnums.ReceivingRequestStatus.PAID;
        }
        if (expired && selfServiceApproved) {
            return BitcoinAccountEnums.ReceivingRequestStatus.AUTO_RESOLUTION_PENDING;
        }
        if (expired) {
            return BitcoinAccountEnums.ReceivingRequestStatus.EXPIRED_RECEIVED;
        }
        return confirmations <= 0
                ? BitcoinAccountEnums.ReceivingRequestStatus.MEMPOOL_SEEN
                : BitcoinAccountEnums.ReceivingRequestStatus.CONFIRMING;
    }

    @Transactional
    public void expireDueRequests() {
        List<BitcoinAccountEnums.ReceivingRequestStatus> active = List.of(
                BitcoinAccountEnums.ReceivingRequestStatus.ACTIVE,
                BitcoinAccountEnums.ReceivingRequestStatus.MEMPOOL_SEEN,
                BitcoinAccountEnums.ReceivingRequestStatus.CONFIRMING);
        List<ReceivingRequestEntity> toUpdate = new ArrayList<>();
        for (ReceivingRequestEntity request : requestRepository
                .findTop200ByStatusInAndExpiresAtBeforeOrderByExpiresAtAsc(active, LocalDateTime.now())) {
            if (request.getStatus() == BitcoinAccountEnums.ReceivingRequestStatus.ACTIVE) {
                request.setStatus(BitcoinAccountEnums.ReceivingRequestStatus.EXPIRED);
                toUpdate.add(request);
            }
        }
        if (!toUpdate.isEmpty()) {
            requestRepository.saveAll(toUpdate);
        }
    }

    @Transactional
    public void purgeReadableReceiveData(LocalDateTime cutoff) {
        List<ReceivingRequestEntity> toUpdate = new ArrayList<>();
        for (ReceivingRequestEntity request : requestRepository.findTop200ByPurgeAfterBefore(cutoff)) {
            if (request.getStatus() == BitcoinAccountEnums.ReceivingRequestStatus.PAID
                    || request.getStatus() == BitcoinAccountEnums.ReceivingRequestStatus.HIDDEN
                    || request.getStatus() == BitcoinAccountEnums.ReceivingRequestStatus.EXPIRED) {
                request.setSelfServiceReason("purged_after_24h_mobile_local_source_of_truth");
                toUpdate.add(request);
            }
        }
        if (!toUpdate.isEmpty()) {
            requestRepository.saveAll(toUpdate);
        }
    }

    private ReceivingRequestEntity requireOwnedRequest(Long userId, UUID requestId) {
        ReceivingRequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Receiving request not found."));
        InternalBtcCardEntity card = cardRepository.findById(request.getCardId())
                .orElseThrow(() -> new IllegalArgumentException("Internal BTC Card not found."));
        accountRepository.findByIdAndUserId(card.getBitcoinAccountId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Receiving request not found."));
        return request;
    }

    private void updateExpiryState(ReceivingRequestEntity request) {
        if (request.getStatus() == BitcoinAccountEnums.ReceivingRequestStatus.ACTIVE && isExpired(request)) {
            request.setStatus(BitcoinAccountEnums.ReceivingRequestStatus.EXPIRED);
            requestRepository.save(request);
        }
    }

    private boolean isExpired(ReceivingRequestEntity request) {
        return request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private boolean safeLatePayment(ReceivingRequestEntity request, long amountSats) {
        return request.getAmountSats() == null || request.getAmountSats() == amountSats;
    }

    private LocalDateTime resolveExpiry(String expiry) {
        String normalized = expiry != null ? expiry.trim().toUpperCase(java.util.Locale.ROOT) : "1H";
        Duration duration = switch (normalized) {
            case "15M", "PT15M" -> Duration.ofMinutes(15);
            case "24H", "P1D" -> Duration.ofHours(24);
            case "PERMANENT" -> null;
            default -> Duration.ofHours(1);
        };
        return duration == null ? null : LocalDateTime.now().plus(duration);
    }

    private String generatePublicCode() {
        byte[] bytes = new byte[18];
        String code;
        do {
            secureRandom.nextBytes(bytes);
            code = "KRS-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (requestRepository.findByPublicCode(code).isPresent());
        return code;
    }

    private Map<String, Object> toView(ReceivingRequestEntity request, ReceivingAddressEntity address, boolean ownerView) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", request.getId());
        view.put("publicCode", request.getPublicCode());
        view.put("amountSats", request.getAmountSats());
        view.put("expiresAt", request.getExpiresAt());
        view.put("createdAt", request.getCreatedAt());
        view.put("paidAt", request.getPaidAt());
        view.put("oneTime", request.isOneTime());
        view.put("status", request.getStatus());
        view.put("network", bitcoinNetwork);
        view.put("address", address.getAddress());
        view.put("bip21", bip21(address.getAddress(), request.getAmountSats()));
        view.put("minimumConfirmations", minimumConfirmations);
        view.put("nextAction", nextAction(request));
        if (ownerView) {
            view.put("cardId", request.getCardId());
            view.put("addressId", request.getAddressId());
            view.put("selfServiceReason", request.getSelfServiceReason());
        }
        return view;
    }

    private Map<String, Object> toOwnerView(ReceivingRequestEntity request, ReceivingAddressEntity address, UUID accountId) {
        Map<String, Object> view = toView(request, address, true);
        view.put("accountId", accountId);
        return view;
    }

    private String nextAction(ReceivingRequestEntity request) {
        return switch (request.getStatus()) {
            case USER_ACTION_REQUIRED -> "CONFIRM_RECOGNIZED_PAYMENT";
            case EXPIRED -> "CREATE_NEW_RECEIVE_REQUEST";
            case AUTO_RESOLUTION_PENDING, EXPIRED_RECEIVED, CONFIRMING -> "WAIT_FOR_NETWORK_CONFIRMATIONS";
            default -> "NONE";
        };
    }

    private String bip21(String address, Long amountSats) {
        if (amountSats == null) {
            return "bitcoin:" + address;
        }
        BigDecimal btc = BigDecimal.valueOf(amountSats)
                .divide(BigDecimal.valueOf(100_000_000L), 8, RoundingMode.DOWN);
        return "bitcoin:" + address + "?amount=" + btc.toPlainString();
    }
}
