package source.ledger.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import source.auth.model.entity.UserDataBase;
import source.ledger.application.balance.LedgerBalanceConsensusPort;
import source.ledger.application.balance.LedgerBalanceUpdate;
import source.ledger.application.balance.LedgerBalanceUpdatePort;
import source.ledger.application.balance.LedgerHashService;
import source.ledger.application.balance.LedgerIntegrityService;
import source.ledger.dto.LedgerDTO;
import source.ledger.entity.LedgerEntity;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.repository.LedgerRepository;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LedgerService Tests")
class LedgerServiceTest {

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private LedgerHashService ledgerHashService;

    @Mock
    private LedgerIntegrityService ledgerIntegrityService;

    @Mock
    private LedgerBalanceConsensusPort balanceConsensusPort;

    @Mock
    private LedgerBalanceUpdatePort balanceUpdatePort;

    @InjectMocks
    private LedgerService ledgerService;

    private WalletEntity wallet;
    private LedgerEntity ledger;
    private UserDataBase user;

    @BeforeEach
    void setUp() {
        user = new UserDataBase();
        ReflectionTestUtils.setField(user, "id", 1L);
        wallet = new WalletEntity();
        wallet.setId(1L);
        wallet.setName("TestWallet");
        wallet.setUser(user);

        ledger = new LedgerEntity(wallet, "Initial context");
        ledger.setId(1);
        ledger.setBalance(BigDecimal.ZERO);
        ledger.setNonce(0);
        ledger.setLastHash("initial-hash");
        ledger.setBalanceSignature("initial-signature");
    }

    @Test
    @DisplayName("Should create ledger successfully")
    void shouldCreateLedgerSuccessfully() {
        when(ledgerRepository.existsByWalletId(wallet.getId())).thenReturn(false);
        when(ledgerHashService.generateInitialHash(wallet.getId())).thenReturn("generated-hash");
        when(ledgerHashService.generateBalanceSignature(any(LedgerEntity.class))).thenReturn("generated-signature");
        when(ledgerRepository.save(any(LedgerEntity.class))).thenReturn(ledger);

        LedgerEntity result = ledgerService.createLedger(wallet, "Initial ledger");

        assertNotNull(result);
        verify(ledgerRepository).existsByWalletId(wallet.getId());
        verify(ledgerHashService).generateInitialHash(wallet.getId());
        verify(ledgerRepository).save(any(LedgerEntity.class));
    }

    @Test
    @DisplayName("Should throw exception when ledger already exists")
    void shouldThrowExceptionWhenLedgerAlreadyExists() {
        when(ledgerRepository.existsByWalletId(wallet.getId())).thenReturn(true);

        assertThrows(LedgerExceptions.LedgerAlreadyExistsException.class,
                () -> ledgerService.createLedger(wallet, "Initial ledger"));

        verify(ledgerRepository).existsByWalletId(wallet.getId());
        verify(ledgerRepository, never()).save(any(LedgerEntity.class));
    }

    @Test
    @DisplayName("Should find ledger by wallet ID")
    void shouldFindLedgerByWalletId() {
        when(ledgerRepository.findByWalletId(wallet.getId())).thenReturn(Optional.of(ledger));

        LedgerEntity result = ledgerService.findByWalletId(wallet.getId());

        assertNotNull(result);
        assertEquals(ledger.getId(), result.getId());
        verify(ledgerRepository).findByWalletId(wallet.getId());
        verify(ledgerIntegrityService).verifyBalanceIntegrity(ledger);
    }

    @Test
    @DisplayName("Should throw exception when ledger not found by wallet ID")
    void shouldThrowExceptionWhenLedgerNotFoundByWalletId() {
        when(ledgerRepository.findByWalletId(wallet.getId())).thenReturn(Optional.empty());

        assertThrows(LedgerExceptions.LedgerNotFoundException.class,
                () -> ledgerService.findByWalletId(wallet.getId()));

        verify(ledgerRepository).findByWalletId(wallet.getId());
    }

    @Test
    @DisplayName("Should find ledgers by user ID")
    void shouldFindLedgersByUserId() {
        List<LedgerEntity> ledgers = Arrays.asList(ledger);
        when(ledgerRepository.findByWalletUserId(user.getId())).thenReturn(ledgers);

        List<LedgerEntity> result = ledgerService.findByUserId(user.getId());

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(ledgerRepository).findByWalletUserId(user.getId());
        verify(ledgerIntegrityService).verifyBalanceIntegrity(ledger);
    }

    @Test
    @DisplayName("Should update balance with credit operation")
    void shouldUpdateBalanceWithCreditOperation() {
        BigDecimal creditAmount = new BigDecimal("100.00");
        when(ledgerRepository.findByWalletIdForUpdate(wallet.getId())).thenReturn(Optional.of(ledger));
        when(ledgerHashService.generateHash(any(LedgerEntity.class))).thenReturn("new-hash");
        when(ledgerHashService.generateBalanceSignature(any(LedgerEntity.class))).thenReturn("new-signature");
        when(ledgerRepository.save(any(LedgerEntity.class))).thenReturn(ledger);

        LedgerEntity result = ledgerService.updateBalance(wallet.getId(), creditAmount, "Credit transaction");

        assertNotNull(result);
        verify(ledgerRepository).findByWalletIdForUpdate(wallet.getId());
        verify(ledgerIntegrityService).verifyBalanceIntegrity(ledger);
        verify(balanceConsensusPort).requireConsensus("new-hash");

        ArgumentCaptor<LedgerBalanceUpdate> updateCaptor = ArgumentCaptor.forClass(LedgerBalanceUpdate.class);
        verify(balanceUpdatePort).publishBalanceUpdated(updateCaptor.capture());
        LedgerBalanceUpdate update = updateCaptor.getValue();
        assertEquals(wallet.getId(), update.walletId());
        assertEquals(wallet.getName(), update.walletName());
        assertEquals(user.getId(), update.userId());
        assertEquals(creditAmount, update.newBalance());
        assertEquals(creditAmount, update.amount());
        assertEquals("Credit transaction", update.context());
    }

    @Test
    @DisplayName("Should throw exception when insufficient balance for debit")
    void shouldThrowExceptionWhenInsufficientBalanceForDebit() {
        ledger.setBalance(new BigDecimal("50.00"));
        BigDecimal debitAmount = new BigDecimal("-100.00");
        when(ledgerRepository.findByWalletIdForUpdate(wallet.getId())).thenReturn(Optional.of(ledger));

        assertThrows(LedgerExceptions.InsufficientBalanceException.class,
                () -> ledgerService.updateBalance(wallet.getId(), debitAmount, "Debit transaction"));

        verify(ledgerRepository).findByWalletIdForUpdate(wallet.getId());
        verify(ledgerRepository, never()).save(any(LedgerEntity.class));
    }

    @Test
    @DisplayName("Should get balance successfully")
    void shouldGetBalanceSuccessfully() {
        BigDecimal expectedBalance = new BigDecimal("150.00");
        ledger.setBalance(expectedBalance);
        when(ledgerRepository.findByWalletId(wallet.getId())).thenReturn(Optional.of(ledger));

        BigDecimal result = ledgerService.getBalance(wallet.getId());

        assertEquals(expectedBalance, result);
        verify(ledgerRepository).findByWalletId(wallet.getId());
        verify(ledgerIntegrityService).verifyBalanceIntegrity(ledger);
    }

    @Test
    @DisplayName("Should delete ledger successfully")
    void shouldDeleteLedgerSuccessfully() {
        when(ledgerRepository.existsByWalletId(wallet.getId())).thenReturn(true);
        doNothing().when(ledgerRepository).deleteByWalletId(wallet.getId());

        assertDoesNotThrow(() -> ledgerService.deleteLedger(wallet.getId()));

        verify(ledgerRepository).existsByWalletId(wallet.getId());
        verify(ledgerRepository).deleteByWalletId(wallet.getId());
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent ledger")
    void shouldThrowExceptionWhenDeletingNonExistentLedger() {
        when(ledgerRepository.existsByWalletId(wallet.getId())).thenReturn(false);

        assertThrows(LedgerExceptions.LedgerNotFoundException.class, () -> ledgerService.deleteLedger(wallet.getId()));

        verify(ledgerRepository).existsByWalletId(wallet.getId());
        verify(ledgerRepository, never()).deleteByWalletId(anyLong());
    }

    @Test
    @DisplayName("Should convert entity to DTO")
    void shouldConvertEntityToDTO() {
        ledger.setBalance(new BigDecimal("100.00"));
        ledger.setNonce(5);
        ledger.setLastHash("test-hash");
        ledger.setContext("Test context");

        LedgerDTO result = ledgerService.toDTO(ledger);

        assertNotNull(result);
        assertEquals(ledger.getId(), result.getId());
        assertEquals(wallet.getId(), result.getWalletId());
        assertEquals(wallet.getName(), result.getWalletName());
        assertEquals(ledger.getBalance(), result.getBalance());
        assertEquals(ledger.getNonce(), result.getNonce());
        assertEquals(ledger.getLastHash(), result.getLastHash());
        assertEquals(ledger.getContext(), result.getContext());
    }

    @Test
    @DisplayName("Should convert list of entities to DTOs")
    void shouldConvertListOfEntitiesToDTOs() {
        LedgerEntity ledger2 = new LedgerEntity(wallet, "Second context");
        ledger2.setId(2);
        List<LedgerEntity> ledgers = Arrays.asList(ledger, ledger2);

        List<LedgerDTO> result = ledgerService.toDTOList(ledgers);

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should validate wallet ownership successfully")
    void shouldValidateWalletOwnershipSuccessfully() {
        assertDoesNotThrow(() -> ledgerService.validateWalletOwnership(wallet, user.getId()));
    }

    @Test
    @DisplayName("Should throw exception when wallet is null")
    void shouldThrowExceptionWhenWalletIsNull() {
        assertThrows(RuntimeException.class, () -> ledgerService.validateWalletOwnership(null, user.getId()));
    }

    @Test
    @DisplayName("Should throw exception when wallet does not belong to user")
    void shouldThrowExceptionWhenWalletDoesNotBelongToUser() {
        assertThrows(RuntimeException.class, () -> ledgerService.validateWalletOwnership(wallet, 999L));
    }
}
