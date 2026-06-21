package source.kfe.application.transaction;

import org.junit.jupiter.api.Test;
import source.kfe.dto.KfeSubmitTransactionRequest;
import source.kfe.model.KfeDirection;
import source.kfe.model.KfeRail;
import source.kfe.repository.KfeTransactionRepository;
import source.kfe.service.KfeBalanceService;
import source.kfe.service.KfeDashboardPublisher;
import source.kfe.service.KfeHashService;
import source.kfe.service.KfePricingService;
import source.kfe.service.KfeQuorumGateway;
import source.kfe.service.KfeResponseMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KfeSubmitTransactionUseCaseTest {

    private final KfeTransactionRepository transactionRepository = mock(KfeTransactionRepository.class);
    private final KfePricingService pricingService = mock(KfePricingService.class);
    private final KfeBalanceService balanceService = mock(KfeBalanceService.class);
    private final KfeQuorumGateway quorumGateway = mock(KfeQuorumGateway.class);
    private final KfeHashService hashService = mock(KfeHashService.class);
    private final KfeResponseMapper responseMapper = mock(KfeResponseMapper.class);
    private final KfeDashboardPublisher dashboardPublisher = mock(KfeDashboardPublisher.class);
    private final KfeTransactionRequestValidator validator = mock(KfeTransactionRequestValidator.class);
    private final KfeTransactionAuthorizationUseCase authorizationUseCase = mock(KfeTransactionAuthorizationUseCase.class);
    private final KfeTransactionIdempotencyUseCase idempotencyUseCase = mock(KfeTransactionIdempotencyUseCase.class);
    private final KfeTransactionWalletResolver walletResolver = mock(KfeTransactionWalletResolver.class);
    private final KfeTransactionStateMachine stateMachine = mock(KfeTransactionStateMachine.class);
    private final KfeBalanceMovementRecorder movementRecorder = mock(KfeBalanceMovementRecorder.class);
    private final KfeTransactionOutboxUseCase outboxUseCase = mock(KfeTransactionOutboxUseCase.class);
    private final KfeTransactionStatementRecorder statementRecorder = mock(KfeTransactionStatementRecorder.class);

    private final KfeSubmitTransactionUseCase useCase = new KfeSubmitTransactionUseCase(
            transactionRepository,
            pricingService,
            balanceService,
            quorumGateway,
            hashService,
            responseMapper,
            dashboardPublisher,
            validator,
            authorizationUseCase,
            idempotencyUseCase,
            walletResolver,
            stateMachine,
            movementRecorder,
            outboxUseCase,
            statementRecorder
    );

    @Test
    void failedIdempotencyReservationDoesNotCreateTransactionIntent() {
        Long userId = 123L;
        KfeSubmitTransactionRequest request = new KfeSubmitTransactionRequest(
                "idemp-key",
                KfeRail.ONCHAIN,
                KfeDirection.OUTBOUND,
                UUID.randomUUID(),
                null,
                100_000L,
                1000L,
                "bcrt1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                "memo",
                "totp-code-123",
                "passkey-json",
                "passphrase"
        );
        String requestHash = "request-hash";

        when(idempotencyUseCase.requestHash(userId, request)).thenReturn(requestHash);
        when(idempotencyUseCase.find(userId, request.idempotencyKey())).thenReturn(null);
        when(idempotencyUseCase.reserve(userId, request, requestHash))
                .thenThrow(new IllegalStateException("duplicate idempotency reservation"));

        assertThrows(IllegalStateException.class, () -> useCase.submit(userId, request));

        verify(idempotencyUseCase).reserve(userId, request, requestHash);
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
