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
import source.ledger.repository.LedgerEntryRepository;

import java.math.BigDecimal;
import java.util.Map;

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
        when(ledgerEntryRepository.calculatePlatformProfitPending()).thenReturn(BigDecimal.ZERO);

        ResponseEntity<Map<String, String>> response = controller.siphonFees("123456", "Yubikey Signature", Map.of());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").contains("No fees"));
        verify(ledgerEntryRepository, never()).markFeesAsCollected();
    }

    @Test
    void shouldExecuteSiphonWhenAuthenticationIsValidAndFeesExist() throws Exception {
        doNothing().when(totpVerifier).totpVerify("founder-secret", "123456");
        when(ledgerEntryRepository.calculatePlatformProfitPending()).thenReturn(new BigDecimal("10.5"));

        ResponseEntity<Map<String, String>> response = controller.siphonFees("123456", "Yubikey Signature", Map.of());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().get("message").contains("Succeeded"));
        assertEquals("10.5", response.getBody().get("amount_withdrawn"));
        verify(ledgerEntryRepository).markFeesAsCollected();
    }
}
