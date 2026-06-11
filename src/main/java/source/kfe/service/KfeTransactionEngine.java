package source.kfe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.kfe.dto.KfeSubmitTransactionRequest;
import source.kfe.dto.KfeTransactionResponse;
import source.kfe.model.KfeBalanceMovementEntity;
import source.kfe.model.KfeDirection;
import source.kfe.model.KfeExecutionOutboxEntity;
import source.kfe.model.KfeIdempotencyEntity;
import source.kfe.model.KfeRail;
import source.kfe.model.KfeTransactionEntity;
import source.kfe.model.KfeTransactionStatus;
import source.kfe.model.KfeWalletEntity;
import source.kfe.model.KfeWalletKind;
import source.kfe.model.KfeWalletStatus;
import source.kfe.repository.KfeBalanceMovementRepository;
import source.kfe.repository.KfeExecutionOutboxRepository;
import source.kfe.repository.KfeIdempotencyRepository;
import source.kfe.repository.KfeTransactionRepository;
import source.kfe.repository.KfeWalletRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class KfeTransactionEngine {

    private static final String ASSET_BTC = "BTC";

    private final KfeTransactionRepository transactionRepository;
    private final KfeIdempotencyRepository idempotencyRepository;
    private final KfeWalletRepository walletRepository;
    private final KfeBalanceMovementRepository movementRepository;
    private final KfeExecutionOutboxRepository outboxRepository;
    private final KfePricingService pricingService;
    private final KfeBalanceService balanceService;
    private final KfeHashService hashService;
    private final KfeAuditLogService auditLogService;
    private final KfeQuorumGateway quorumGateway;
    private final KfeStatementService statementService;
    private final KfeResponseMapper responseMapper;
    private final KfeDashboardPublisher dashboardPublisher;
    private final ObjectMapper objectMapper;

    public KfeTransactionEngine(
            KfeTransactionRepository transactionRepository,
            KfeIdempotencyRepository idempotencyRepository,
            KfeWalletRepository walletRepository,
            KfeBalanceMovementRepository movementRepository,
            KfeExecutionOutboxRepository outboxRepository,
            KfePricingService pricingService,
            KfeBalanceService balanceService,
            KfeHashService hashService,
            KfeAuditLogService auditLogService,
            KfeQuorumGateway quorumGateway,
            KfeStatementService statementService,
            KfeResponseMapper responseMapper,
            KfeDashboardPublisher dashboardPublisher,
            ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.walletRepository = walletRepository;
        this.movementRepository = movementRepository;
        this.outboxRepository = outboxRepository;
        this.pricingService = pricingService;
        this.balanceService = balanceService;
        this.hashService = hashService;
        this.auditLogService = auditLogService;
        this.quorumGateway = quorumGateway;
        this.statementService = statementService;
        this.responseMapper = responseMapper;
        this.dashboardPublisher = dashboardPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public KfeTransactionResponse submit(Long userId, KfeSubmitTransactionRequest request) {
        validateRequest(request);
        String requestHash = requestHash(userId, request);
        KfeIdempotencyEntity existingIdempotency = idempotencyRepository.findById(request.idempotencyKey()).orElse(null);
        if (existingIdempotency != null) {
            if (!existingIdempotency.getRequestHash().equals(requestHash)) {
                throw new IllegalStateException("Idempotency key was reused with a different transaction payload.");
            }
            return transactionRepository.findById(existingIdempotency.getTransactionId())
                    .map(responseMapper::toTransactionResponse)
                    .orElseThrow(() -> new IllegalStateException("Idempotent transaction record is missing."));
        }

        KfeTransactionEntity tx = createIntent(userId, request);
        KfeIdempotencyEntity idempotency = reserveIdempotency(request, requestHash, tx);
        audit(tx, "KFE_TRANSACTION_INTENT", null, tx.getStatus(), null);

        transition(tx, KfeTransactionStatus.VALIDATING, "KFE_TRANSACTION_VALIDATING",
                Map.of("requestHash", requestHash));
        KfeWalletEntity sourceWallet = resolveSourceWallet(userId, request);
        KfeWalletEntity destinationWallet = resolveDestinationWallet(userId, request);
        KfePricingService.Quote quote = pricingService.quote(
                request.rail(),
                request.direction(),
                request.amountSats(),
                request.networkFeeSats());
        applyQuote(tx, quote);

        String proposalHash = proposalHash(tx, request);
        tx.setQuorumProposalHash(proposalHash);
        transition(tx, KfeTransactionStatus.QUORUM_SYNC, "KFE_TRANSACTION_QUORUM_SYNC",
                Map.of("proposalHash", proposalHash));
        KfeQuorumGateway.Result quorum = quorumGateway.requireHealthyUnanimousConsensus(proposalHash);
        tx.setQuorumAckCount(quorum.acceptedNodes());

        if (requiresSourceReserve(request)) {
            balanceService.reserve(sourceWallet.getId(), ASSET_BTC, tx.getTotalDebitSats());
            movement(tx.getId(), sourceWallet.getId(), "RESERVE", tx.getTotalDebitSats(), "AVAILABLE", "LOCKED");
        }
        transition(tx, KfeTransactionStatus.LOCKED, "KFE_TRANSACTION_LOCKED",
                Map.of("proposalHash", proposalHash, "quorumAckCount", quorum.acceptedNodes()));

        if (request.rail() == KfeRail.INTERNAL || request.direction() == KfeDirection.INTERNAL) {
            settleInternal(userId, tx, sourceWallet, destinationWallet);
        } else {
            enqueueExternal(tx, request);
            transition(tx, KfeTransactionStatus.EXECUTING, "KFE_TRANSACTION_EXECUTING",
                    Map.of("proposalHash", proposalHash, "rail", tx.getRail().name()));
            recordStatement(userId, tx, sourceWallet != null ? sourceWallet.getId() : tx.getDestinationWalletId(), request);
        }

        idempotency.setTransactionId(tx.getId());
        idempotency.setStatus(tx.getStatus().name());
        idempotencyRepository.save(idempotency);
        dashboardPublisher.publishAfterCommit(userId);
        if (destinationWallet != null && !destinationWallet.getUserId().equals(userId)) {
            dashboardPublisher.publishAfterCommit(destinationWallet.getUserId());
        }
        return responseMapper.toTransactionResponse(transactionRepository.save(tx));
    }

    private KfeTransactionEntity createIntent(Long userId, KfeSubmitTransactionRequest request) {
        KfeTransactionEntity tx = new KfeTransactionEntity();
        tx.setUserId(userId);
        tx.setIdempotencyKey(request.idempotencyKey());
        tx.setRail(request.rail());
        tx.setDirection(request.direction());
        tx.setSourceWalletId(request.sourceWalletId());
        tx.setDestinationWalletId(request.destinationWalletId());
        tx.setGrossAmountSats(request.amountSats());
        return transactionRepository.save(tx);
    }

    private KfeIdempotencyEntity reserveIdempotency(
            KfeSubmitTransactionRequest request,
            String requestHash,
            KfeTransactionEntity tx) {
        KfeIdempotencyEntity idempotency = new KfeIdempotencyEntity();
        idempotency.setIdempotencyKey(request.idempotencyKey());
        idempotency.setTransactionId(tx.getId());
        idempotency.setRequestHash(requestHash);
        idempotency.setStatus(tx.getStatus().name());
        return idempotencyRepository.save(idempotency);
    }

    private void settleInternal(
            Long userId,
            KfeTransactionEntity tx,
            KfeWalletEntity sourceWallet,
            KfeWalletEntity destinationWallet) {
        balanceService.settleReservedDebit(sourceWallet.getId(), ASSET_BTC, tx.getTotalDebitSats());
        movement(tx.getId(), sourceWallet.getId(), "SETTLE_DEBIT", tx.getTotalDebitSats(), "LOCKED", null);
        balanceService.creditAvailable(destinationWallet.getId(), ASSET_BTC, tx.getReceiverAmountSats());
        movement(tx.getId(), destinationWallet.getId(), "CREDIT", tx.getReceiverAmountSats(), null, "AVAILABLE");
        transition(tx, KfeTransactionStatus.SETTLED, "KFE_TRANSACTION_SETTLED",
                Map.of("rail", tx.getRail().name()));

        recordStatement(userId, tx, sourceWallet.getId(), null);
        if (!destinationWallet.getUserId().equals(userId)) {
            recordStatement(destinationWallet.getUserId(), tx, destinationWallet.getId(), null);
        }
    }

    private void enqueueExternal(KfeTransactionEntity tx, KfeSubmitTransactionRequest request) {
        String payloadJson = outboxPayload(tx, request);
        KfeExecutionOutboxEntity outbox = new KfeExecutionOutboxEntity();
        outbox.setTransactionId(tx.getId());
        outbox.setOperation(tx.getRail().name() + "_" + tx.getDirection().name());
        outbox.setPayloadJson(payloadJson);
        outbox.setPayloadHash(hashService.sha256(payloadJson));
        outbox.setNextAttemptAt(java.time.LocalDateTime.now());
        outboxRepository.save(outbox);
    }

    private String outboxPayload(KfeTransactionEntity tx, KfeSubmitTransactionRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", tx.getId().toString());
        payload.put("idempotencyKey", tx.getIdempotencyKey());
        payload.put("userId", tx.getUserId());
        payload.put("rail", tx.getRail().name());
        payload.put("direction", tx.getDirection().name());
        payload.put("sourceWalletId", tx.getSourceWalletId());
        payload.put("destinationWalletId", tx.getDestinationWalletId());
        payload.put("amountSats", tx.getReceiverAmountSats());
        payload.put("networkFeeSats", tx.getNetworkFeeSats());
        payload.put("totalDebitSats", tx.getTotalDebitSats());
        payload.put("externalReference", request.externalReference());
        payload.put("memo", request.memo());
        payload.put("quorumProposalHash", tx.getQuorumProposalHash());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize KFE outbox payload.", exception);
        }
    }

    private void applyQuote(KfeTransactionEntity tx, KfePricingService.Quote quote) {
        tx.setGrossAmountSats(quote.grossAmountSats());
        tx.setReceiverAmountSats(quote.receiverAmountSats());
        tx.setNetworkFeeSats(quote.networkFeeSats());
        tx.setKeroseneFeeSats(quote.keroseneFeeSats());
        tx.setTotalDebitSats(quote.totalDebitSats());
        transactionRepository.save(tx);
    }

    private KfeWalletEntity resolveSourceWallet(Long userId, KfeSubmitTransactionRequest request) {
        if (!requiresSourceReserve(request)) {
            return null;
        }
        if (request.sourceWalletId() == null) {
            throw new IllegalArgumentException("sourceWalletId is required.");
        }
        KfeWalletEntity wallet = walletRepository.findByIdAndUserIdForUpdate(request.sourceWalletId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Source KFE wallet not found."));
        requireSpendable(wallet, "source");
        return wallet;
    }

    private KfeWalletEntity resolveDestinationWallet(Long userId, KfeSubmitTransactionRequest request) {
        if (request.direction() == KfeDirection.OUTBOUND) {
            return null;
        }
        if (request.destinationWalletId() == null) {
            throw new IllegalArgumentException("destinationWalletId is required.");
        }
        KfeWalletEntity wallet = walletRepository.findById(request.destinationWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Destination KFE wallet not found."));
        if (request.direction() == KfeDirection.INBOUND && !wallet.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Inbound destination wallet must belong to the authenticated user.");
        }
        requireSpendable(wallet, "destination");
        return wallet;
    }

    private void requireSpendable(KfeWalletEntity wallet, String role) {
        if (wallet.getStatus() != KfeWalletStatus.ACTIVE) {
            throw new IllegalStateException(role + " wallet is not active.");
        }
        if (wallet.getKind() == KfeWalletKind.WATCH_ONLY || !wallet.isSpendable()) {
            throw new IllegalStateException(role + " wallet is watch-only and cannot move funds.");
        }
    }

    private boolean requiresSourceReserve(KfeSubmitTransactionRequest request) {
        return request.direction() == KfeDirection.OUTBOUND || request.direction() == KfeDirection.INTERNAL;
    }

    private void transition(
            KfeTransactionEntity tx,
            KfeTransactionStatus target,
            String eventType,
            Map<String, ?> auditPayload) {
        KfeTransactionStatus previous = tx.getStatus();
        tx.setStatus(target);
        transactionRepository.save(tx);
        audit(tx, eventType, previous, target, auditPayload);
    }

    private void audit(
            KfeTransactionEntity tx,
            String eventType,
            KfeTransactionStatus from,
            KfeTransactionStatus to,
            Map<String, ?> payload) {
        Map<String, Object> redacted = new LinkedHashMap<>();
        redacted.put("transactionId", tx.getId().toString());
        redacted.put("idempotencyHash", hashService.sha256(tx.getIdempotencyKey()));
        if (payload != null) {
            redacted.putAll(payload);
        }
        auditLogService.record(eventType, tx.getId(), tx.getSourceWalletId(), from, to, redacted);
    }

    private void movement(
            UUID transactionId,
            UUID walletId,
            String movementType,
            long amountSats,
            String fromBucket,
            String toBucket) {
        KfeBalanceMovementEntity movement = new KfeBalanceMovementEntity();
        movement.setTransactionId(transactionId);
        movement.setWalletId(walletId);
        movement.setMovementType(movementType);
        movement.setAmountSats(amountSats);
        movement.setFromBucket(fromBucket);
        movement.setToBucket(toBucket);
        movementRepository.save(movement);
    }

    private void recordStatement(
            Long userId,
            KfeTransactionEntity tx,
            UUID walletId,
            KfeSubmitTransactionRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", tx.getId().toString());
        payload.put("status", tx.getStatus().name());
        payload.put("rail", tx.getRail().name());
        payload.put("direction", tx.getDirection().name());
        payload.put("grossAmountSats", tx.getGrossAmountSats());
        payload.put("receiverAmountSats", tx.getReceiverAmountSats());
        payload.put("networkFeeSats", tx.getNetworkFeeSats());
        payload.put("keroseneFeeSats", tx.getKeroseneFeeSats());
        payload.put("totalDebitSats", tx.getTotalDebitSats());
        if (request != null && request.memo() != null && !request.memo().isBlank()) {
            payload.put("memo", request.memo());
        }
        statementService.recordUserStatement(userId, walletId, tx, payload);
    }

    private String proposalHash(KfeTransactionEntity tx, KfeSubmitTransactionRequest request) {
        return hashService.sha256(String.join("|",
                "KFE_TX_PROPOSAL",
                tx.getId().toString(),
                tx.getUserId().toString(),
                tx.getRail().name(),
                tx.getDirection().name(),
                String.valueOf(tx.getSourceWalletId()),
                String.valueOf(tx.getDestinationWalletId()),
                String.valueOf(tx.getGrossAmountSats()),
                String.valueOf(tx.getReceiverAmountSats()),
                String.valueOf(tx.getNetworkFeeSats()),
                String.valueOf(tx.getKeroseneFeeSats()),
                String.valueOf(tx.getTotalDebitSats()),
                safe(request.externalReference())));
    }

    private String requestHash(Long userId, KfeSubmitTransactionRequest request) {
        return hashService.sha256(String.join("|",
                "KFE_TX_REQUEST",
                userId.toString(),
                request.rail().name(),
                request.direction().name(),
                String.valueOf(request.sourceWalletId()),
                String.valueOf(request.destinationWalletId()),
                String.valueOf(request.amountSats()),
                String.valueOf(request.networkFeeSats()),
                safe(request.externalReference()),
                safe(request.memo())));
    }

    private void validateRequest(KfeSubmitTransactionRequest request) {
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required.");
        }
        if (request.idempotencyKey().length() > 180) {
            throw new IllegalArgumentException("idempotencyKey must have at most 180 characters.");
        }
        if (request.amountSats() <= 0) {
            throw new IllegalArgumentException("amountSats must be positive.");
        }
        if (request.rail() == KfeRail.INTERNAL && request.direction() != KfeDirection.INTERNAL) {
            throw new IllegalArgumentException("INTERNAL rail requires INTERNAL direction.");
        }
        if (request.direction() == KfeDirection.INTERNAL && request.rail() != KfeRail.INTERNAL) {
            throw new IllegalArgumentException("INTERNAL direction requires INTERNAL rail.");
        }
        if (request.rail() != KfeRail.INTERNAL
                && request.direction() == KfeDirection.OUTBOUND
                && (request.externalReference() == null || request.externalReference().isBlank())) {
            throw new IllegalArgumentException("externalReference is required for external outbound transactions.");
        }
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
