package source.ledger.application.balance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import source.auth.model.entity.UserDataBase;
import source.ledger.entity.LedgerEntity;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.repository.LedgerRepository;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LedgerIntegrityService Tests")
class LedgerIntegrityServiceTest {

    @Mock
    private LedgerHashService ledgerHashService;

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private LedgerIntegrityFailurePort integrityFailurePort;

    @InjectMocks
    private LedgerIntegrityService ledgerIntegrityService;

    private LedgerEntity ledger;

    @BeforeEach
    void setUp() {
        UserDataBase user = new UserDataBase();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setUsername("satoshi");

        WalletEntity wallet = new WalletEntity();
        wallet.setId(7L);
        wallet.setName("cold");
        wallet.setUser(user);

        ledger = new LedgerEntity(wallet, "Initial");
        ledger.setId(99);
        ledger.setBalance(new BigDecimal("1.00000000"));
        ledger.setNonce(0);
        ledger.setLastHash("hash");
        ledger.setBalanceSignature("stored-signature");
    }

    @Test
    @DisplayName("Should initialize missing balance signature without reporting failure")
    void shouldInitializeMissingBalanceSignatureWithoutReportingFailure() {
        ledger.setBalanceSignature(null);
        when(ledgerHashService.generateBalanceSignature(ledger)).thenReturn("generated-signature");

        ledgerIntegrityService.verifyBalanceIntegrity(ledger);

        assertEquals("generated-signature", ledger.getBalanceSignature());
        verify(ledgerRepository).save(ledger);
        verifyNoInteractions(integrityFailurePort);
    }

    @Test
    @DisplayName("Should report integrity failure through output port")
    void shouldReportIntegrityFailureThroughOutputPort() {
        when(ledgerHashService.generateBalanceSignature(ledger)).thenReturn("expected-signature");

        assertThrows(
                LedgerExceptions.LedgerIntegrityViolationException.class,
                () -> ledgerIntegrityService.verifyBalanceIntegrity(ledger));

        ArgumentCaptor<LedgerIntegrityFailure> failureCaptor =
                ArgumentCaptor.forClass(LedgerIntegrityFailure.class);
        verify(integrityFailurePort).reportIntegrityFailure(failureCaptor.capture());
        LedgerIntegrityFailure failure = failureCaptor.getValue();
        assertEquals(99, failure.ledgerId());
        assertEquals(7L, failure.walletId());
        assertEquals(42L, failure.userId());
        assertEquals("BALANCE_SIGNATURE_MISMATCH", failure.reason());
        verify(ledgerRepository, never()).save(ledger);
    }
}
