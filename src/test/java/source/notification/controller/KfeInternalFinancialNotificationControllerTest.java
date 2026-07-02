package source.notification.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import source.common.financial.FinancialDepositConfirmedNotificationRequest;
import source.common.financial.FinancialNotificationPort;
import source.common.financial.FinancialPaymentRequestDepositConfirmedNotificationRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KfeInternalFinancialNotificationControllerTest {

    private final FinancialNotificationPort notificationPort = mock(FinancialNotificationPort.class);
    private final KfeInternalFinancialNotificationController controller =
            new KfeInternalFinancialNotificationController(notificationPort, "credential");

    @Test
    void forwardsDepositConfirmedNotificationWhenCredentialMatches() {
        UUID transactionId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        controller.notifyDepositConfirmed(
                "credential",
                new FinancialDepositConfirmedNotificationRequest(
                        42L,
                        transactionId,
                        walletId,
                        "ONCHAIN",
                        1500L,
                        3));

        verify(notificationPort).notifyDepositConfirmed(42L, transactionId, walletId, "ONCHAIN", 1500L, 3);
    }

    @Test
    void forwardsPaymentRequestDepositConfirmedNotificationWhenCredentialMatches() {
        UUID transactionId = UUID.randomUUID();
        UUID paymentRequestId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        controller.notifyPaymentRequestDepositConfirmed(
                "credential",
                new FinancialPaymentRequestDepositConfirmedNotificationRequest(
                        42L,
                        transactionId,
                        paymentRequestId,
                        "public-id",
                        walletId,
                        "LIGHTNING",
                        2500L));

        verify(notificationPort).notifyPaymentRequestDepositConfirmed(
                42L,
                transactionId,
                paymentRequestId,
                "public-id",
                walletId,
                "LIGHTNING",
                2500L);
    }

    @Test
    void rejectsInvalidCredential() {
        assertThrows(
                ResponseStatusException.class,
                () -> controller.notifyDepositConfirmed(
                        "wrong",
                        new FinancialDepositConfirmedNotificationRequest(
                                42L,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "ONCHAIN",
                                1500L,
                                3)));
    }

    @Test
    void rejectsMissingUserId() {
        assertThrows(
                ResponseStatusException.class,
                () -> controller.notifyDepositConfirmed(
                        "credential",
                        new FinancialDepositConfirmedNotificationRequest(
                                null,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                "ONCHAIN",
                                1500L,
                                3)));
    }
}
