package source.kfe.application.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.kfe.dto.KfeSubmitTransactionRequest;
import source.kfe.dto.KfeTransactionResponse;
import source.kfe.model.KfeDirection;
import source.kfe.model.KfeIdempotencyEntity;
import source.kfe.model.KfeRail;
import source.kfe.model.KfeTransactionEntity;
import source.kfe.model.KfeTransactionStatus;
import source.kfe.model.KfeWalletEntity;
import source.kfe.repository.KfeTransactionRepository;
import source.kfe.service.KfeBalanceService;
import source.kfe.service.KfeDashboardPublisher;
import source.kfe.service.KfeHashService;
import source.kfe.service.KfePricingService;
import source.kfe.service.KfeQuorumGateway;
import source.kfe.service.KfeResponseMapper;

import java.util.Map;

@Service
public class KfeSubmitTransactionUseCase {

    private static final String ASSET_BTC = "BTC";

    private final KfeTransactionRepository transactionRepository;
    private final KfePricingService pricingService;
    private final KfeBalanceService balanceService;
    private final KfeQuorumGateway quorumGateway;
    private final KfeHashService hashService;
    private final KfeResponseMapper responseMapper;
    private final KfeDashboardPublisher dashboardPublisher;
    private final KfeTransactionRequestValidator validator;
    private final KfeTransactionAuthorizationUseCase authorizationUseCase;
    private final KfeTransactionIdempotencyUseCase idempotencyUseCase;
    private final KfeTransactionWalletResolver walletResolver;
    private final KfeTransactionStateMachine stateMachine;
    private final KfeBalanceMovementRecorder movementRecorder;
    private final KfeTransactionOutboxUseCase outboxUseCase;
    private final KfeTransactionStatementRecorder statementRecorder;

    public KfeSubmitTransactionUseCase(
            KfeTransactionRepository transactionRepository,
            KfePricingService pricingService,
            KfeBalanceService balanceService,
            KfeQuorumGateway quorumGateway,
            KfeHashService hashService,
            KfeResponseMapper responseMapper,
            KfeDashboardPublisher dashboardPublisher,
            KfeTransactionRequestValidator validator,
            KfeTransactionAuthorizationUseCase authorizationUseCase,
            KfeTransactionIdempotencyUseCase idempotencyUseCase,
            KfeTransactionWalletResolver walletResolver,
            KfeTransactionStateMachine stateMachine,
            KfeBalanceMovementRecorder movementRecorder,
            KfeTransactionOutboxUseCase outboxUseCase,
            KfeTransactionStatementRecorder statementRecorder) {
        this.transactionRepository = transactionRepository;
        this.pricingService = pricingService;
        this.balanceService = balanceService;
        this.quorumGateway = quorumGateway;
        this.hashService = hashService;
        this.responseMapper = responseMapper;
        this.dashboardPublisher = dashboardPublisher;
        this.validator = validator;
        this.authorizationUseCase = authorizationUseCase;
        this.idempotencyUseCase = idempotencyUseCase;
        this.walletResolver = walletResolver;
        this.stateMachine = stateMachine;
        this.movementRecorder = movementRecorder;
        this.outboxUseCase = outboxUseCase;
        this.statementRecorder = statementRecorder;
    }

    @Transactional
    public KfeTransactionResponse submit(Long userId, KfeSubmitTransactionRequest request) {
        validator.validate(request);
        authorizationUseCase.authorize(userId, request);

        String requestHash = idempotencyUseCase.requestHash(userId, request);
        KfeIdempotencyEntity existingIdempotency = idempotencyUseCase.find(userId, request.idempotencyKey());
        if (existingIdempotency != null) {
            return idempotencyUseCase.existingResponse(existingIdempotency, requestHash);
        }

        KfeTransactionEntity tx = createIntent(userId, request);
        KfeIdempotencyEntity idempotency = idempotencyUseCase.reserve(request, requestHash, tx);
        stateMachine.audit(tx, "KFE_TRANSACTION_INTENT", null, tx.getStatus(), null);

        stateMachine.transition(tx, KfeTransactionStatus.VALIDATING, "KFE_TRANSACTION_VALIDATING",
                Map.of("requestHash", requestHash));
        KfeWalletEntity sourceWallet = walletResolver.resolveSourceWallet(userId, request);
        KfeWalletEntity destinationWallet = walletResolver.resolveDestinationWallet(userId, request);
        applyQuote(tx, pricingService.quote(request.rail(), request.direction(), request.amountSats(), request.networkFeeSats()));

        String proposalHash = proposalHash(tx, request);
        tx.setQuorumProposalHash(proposalHash);
        stateMachine.transition(tx, KfeTransactionStatus.QUORUM_SYNC, "KFE_TRANSACTION_QUORUM_SYNC",
                Map.of("proposalHash", proposalHash));
        KfeQuorumGateway.Result quorum = quorumGateway.requireHealthyUnanimousConsensus(proposalHash);
        tx.setQuorumAckCount(quorum.acceptedNodes());

        if (walletResolver.requiresSourceReserve(request)) {
            balanceService.reserve(sourceWallet.getId(), ASSET_BTC, tx.getTotalDebitSats());
            movementRecorder.record(tx.getId(), sourceWallet.getId(), "RESERVE", tx.getTotalDebitSats(), "AVAILABLE", "LOCKED");
        }
        stateMachine.transition(tx, KfeTransactionStatus.LOCKED, "KFE_TRANSACTION_LOCKED",
                Map.of("proposalHash", proposalHash, "quorumAckCount", quorum.acceptedNodes()));

        if (request.rail() == KfeRail.INTERNAL || request.direction() == KfeDirection.INTERNAL) {
            settleInternal(userId, tx, sourceWallet, destinationWallet);
        } else {
            outboxUseCase.enqueueExternal(tx, request);
            stateMachine.transition(tx, KfeTransactionStatus.EXECUTING, "KFE_TRANSACTION_EXECUTING",
                    Map.of("proposalHash", proposalHash, "rail", tx.getRail().name()));
            statementRecorder.record(userId, tx, sourceWallet != null ? sourceWallet.getId() : tx.getDestinationWalletId(), request);
        }

        idempotencyUseCase.complete(idempotency, tx);
        publishDashboards(userId, destinationWallet);
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

    private void settleInternal(
            Long userId,
            KfeTransactionEntity tx,
            KfeWalletEntity sourceWallet,
            KfeWalletEntity destinationWallet) {
        balanceService.settleReservedDebit(sourceWallet.getId(), ASSET_BTC, tx.getTotalDebitSats());
        movementRecorder.record(tx.getId(), sourceWallet.getId(), "SETTLE_DEBIT", tx.getTotalDebitSats(), "LOCKED", null);
        balanceService.creditAvailable(destinationWallet.getId(), ASSET_BTC, tx.getReceiverAmountSats());
        movementRecorder.record(tx.getId(), destinationWallet.getId(), "CREDIT", tx.getReceiverAmountSats(), null, "AVAILABLE");
        stateMachine.transition(tx, KfeTransactionStatus.SETTLED, "KFE_TRANSACTION_SETTLED",
                Map.of("rail", tx.getRail().name()));

        statementRecorder.record(userId, tx, sourceWallet.getId(), null);
        if (!destinationWallet.getUserId().equals(userId)) {
            statementRecorder.record(destinationWallet.getUserId(), tx, destinationWallet.getId(), null);
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

    private void publishDashboards(Long userId, KfeWalletEntity destinationWallet) {
        dashboardPublisher.publishAfterCommit(userId);
        if (destinationWallet != null && !destinationWallet.getUserId().equals(userId)) {
            dashboardPublisher.publishAfterCommit(destinationWallet.getUserId());
        }
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
