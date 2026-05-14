package source.ledger.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import source.auth.application.service.validation.totp.contracts.TOTPVerifier;
import source.ledger.entity.SiphonRequest;
import source.ledger.entity.SiphonRequestStatus;
import source.ledger.repository.LedgerEntryRepository;
import source.treasury.service.TreasuryPayoutService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerAuditControllerTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private TOTPVerifier totpVerifier;

    @Mock
    private TreasuryPayoutService treasuryPayoutService;

    @InjectMocks
    private LedgerAuditController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "founderTotpSecret", "founder-secret");
        ReflectionTestUtils.setField(controller, "expectedHardwareSignature", "Yubikey Signature");
    }

    @Test
    void shouldRejectSiphonWhenFounderSecretIsMissing() {
        ReflectionTestUtils.setField(controller, "founderTotpSecret", "");

        ResponseEntity<Map<String, String>> response = controller.siphonFees("123456", "Yubikey Signature", Map.of());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertTrue(response.getBody().get("error").contains("not configured"));
        verify(ledgerEntryRepository, never()).markFeesAsCollected();
    }

    @Test
    void shouldRejectSiphonWhenHardwareSignatureDoesNotMatch() throws Exception {
        doNothing().when(totpVerifier).totpVerify("founder-secret", "123456");

        ResponseEntity<Map<String, String>> response = controller.siphonFees("123456", "wrong-signature", Map.of());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().get("error").contains("Hardware"));
        verify(ledgerEntryRepository, never()).markFeesAsCollected();
    }

    @Test
    void shouldReturnBadRequestWhenNoFeesAreAvailable() throws Exception {
        doNothing().when(totpVerifier).totpVerify("founder-secret", "123456");
        when(treasuryPayoutService.requestPayout("payout-1", null, null))
                .thenThrow(new IllegalArgumentException("No platform fees are available for payout."));

        ResponseEntity<?> response = controller.siphonFees(
                "123456",
                "Yubikey Signature",
                Map.of("idempotencyKey", "payout-1"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(((Map<?, ?>) response.getBody()).get("error").toString().contains("No platform fees"));
        verify(ledgerEntryRepository, never()).markFeesAsCollected();
    }

    @Test
    void shouldRejectSiphonWhenIdempotencyKeyIsMissing() throws Exception {
        doNothing().when(totpVerifier).totpVerify("founder-secret", "123456");
        when(treasuryPayoutService.requestPayout(null, null, null))
                .thenThrow(new IllegalArgumentException("idempotencyKey is required for treasury payout."));

        ResponseEntity<Map<String, String>> response = controller.siphonFees("123456", "Yubikey Signature", Map.of());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("error").contains("idempotencyKey"));
        verify(ledgerEntryRepository, never()).markFeesAsCollected();
    }

    @Test
    void shouldQueuePayoutWhenAuthenticationIsValidAndFeesExist() throws Exception {
        doNothing().when(totpVerifier).totpVerify("founder-secret", "123456");
        SiphonRequest requested = payoutRequest(SiphonRequestStatus.REQUESTED);
        SiphonRequest queued = payoutRequest(SiphonRequestStatus.QUEUED);
        when(treasuryPayoutService.requestPayout("payout-1", null, null)).thenReturn(requested);
        when(treasuryPayoutService.approveAndQueue(
                org.mockito.ArgumentMatchers.eq(requested.getId()),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.startsWith("totp="))).thenReturn(queued);

        ResponseEntity<Map<String, String>> response = controller.siphonFees(
                "123456",
                "Yubikey Signature",
                Map.of("idempotencyKey", "payout-1"));

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertTrue(response.getBody().get("message").contains("queued"));
        assertEquals("QUEUED", response.getBody().get("status"));
        assertEquals("0.00001000", response.getBody().get("amount_withdrawn"));
        verify(ledgerEntryRepository, never()).markFeesAsCollected();
    }

    private SiphonRequest payoutRequest(SiphonRequestStatus status) {
        LocalDateTime now = LocalDateTime.now();
        SiphonRequest request = new SiphonRequest();
        request.setId(UUID.randomUUID());
        request.setAmount(new BigDecimal("0.00001000"));
        request.setDestinationAddress("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh");
        request.setIdempotencyKey("payout-1");
        request.setRequestedAt(now);
        request.setRevenueCutoffAt(now);
        request.setExecutableAfter(now);
        request.setNextAttemptAt(now);
        request.setStatus(status);
        return request;
    }
}
