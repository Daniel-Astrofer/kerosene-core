package source.kfe.service;

import org.junit.jupiter.api.Test;
import source.common.service.AddressDerivationService;
import source.kfe.model.KfePaymentRequestEntity;
import source.kfe.model.KfePaymentRequestStatus;
import source.kfe.repository.KfePaymentRequestRepository;
import source.kfe.repository.KfeWalletAddressRepository;
import source.kfe.repository.KfeWalletRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KfePaymentRequestServiceTest {

    private final KfePaymentRequestRepository paymentRequestRepository = mock(KfePaymentRequestRepository.class);
    private final KfeWalletRepository walletRepository = mock(KfeWalletRepository.class);
    private final KfeWalletAddressRepository addressRepository = mock(KfeWalletAddressRepository.class);
    private final KfeWalletService walletService = mock(KfeWalletService.class);
    private final AddressDerivationService addressDerivationService = mock(AddressDerivationService.class);
    private final KfeReceiveAddressIssuer receiveAddressIssuer = mock(KfeReceiveAddressIssuer.class);
    private final KfeAuditLogService auditLogService = mock(KfeAuditLogService.class);

    private final KfePaymentRequestService service = new KfePaymentRequestService(
            paymentRequestRepository,
            walletRepository,
            addressRepository,
            walletService,
            addressDerivationService,
            receiveAddressIssuer,
            auditLogService);

    @Test
    void publicGetExpiresOverdueOpenRequestBeforeReturningIt() {
        KfePaymentRequestEntity paymentRequest = paymentRequest();
        paymentRequest.setStatus(KfePaymentRequestStatus.OPEN);
        paymentRequest.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(paymentRequestRepository.findByPublicId("public-id")).thenReturn(Optional.of(paymentRequest));
        when(paymentRequestRepository.save(paymentRequest)).thenReturn(paymentRequest);

        var response = service.publicGet("public-id");

        assertThat(response.status()).isEqualTo(KfePaymentRequestStatus.EXPIRED);
        assertThat(paymentRequest.getStatus()).isEqualTo(KfePaymentRequestStatus.EXPIRED);
        verify(paymentRequestRepository).save(paymentRequest);
    }

    @Test
    void cancelDoesNotRegressExpiredPaymentRequest() {
        UUID id = UUID.randomUUID();
        KfePaymentRequestEntity paymentRequest = paymentRequest();
        paymentRequest.setStatus(KfePaymentRequestStatus.EXPIRED);
        when(paymentRequestRepository.findByIdAndUserId(id, 7L)).thenReturn(Optional.of(paymentRequest));

        var response = service.cancel(7L, id);

        assertThat(response.status()).isEqualTo(KfePaymentRequestStatus.EXPIRED);
        verify(paymentRequestRepository, never()).save(paymentRequest);
        verify(auditLogService, never()).record(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    private KfePaymentRequestEntity paymentRequest() {
        KfePaymentRequestEntity paymentRequest = new KfePaymentRequestEntity();
        paymentRequest.setPublicId("public-id");
        paymentRequest.setUserId(7L);
        paymentRequest.setWalletId(UUID.randomUUID());
        paymentRequest.setAddressId(UUID.randomUUID());
        paymentRequest.setAddress("bcrt1qpaymentrequest");
        return paymentRequest;
    }
}
