package source.bitcoinaccounts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import source.bitcoinaccounts.model.BitcoinAccountEntity;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.model.InternalBtcCardEntity;
import source.bitcoinaccounts.model.LedgerEntryEntity;
import source.bitcoinaccounts.model.ReceivingAddressEntity;
import source.bitcoinaccounts.model.ReceivingRequestEntity;
import source.bitcoinaccounts.repository.BitcoinAccountRepository;
import source.bitcoinaccounts.repository.InternalBtcCardRepository;
import source.bitcoinaccounts.repository.ReceivingAddressRepository;
import source.bitcoinaccounts.repository.ReceivingRequestRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceivingRequestServiceTest {

    private ReceivingRequestRepository requestRepository;
    private ReceivingAddressRepository addressRepository;
    private InternalBtcCardRepository cardRepository;
    private BitcoinAccountRepository accountRepository;
    private BitcoinAccountService accountService;
    private BitcoinReceiveAddressIssuer addressIssuer;
    private BitcoinAccountLedgerService ledgerService;
    private BitcoinTaxEventService taxEventService;
    private ReceivingRequestService service;

    @BeforeEach
    void setUp() {
        requestRepository = mock(ReceivingRequestRepository.class);
        addressRepository = mock(ReceivingAddressRepository.class);
        cardRepository = mock(InternalBtcCardRepository.class);
        accountRepository = mock(BitcoinAccountRepository.class);
        accountService = mock(BitcoinAccountService.class);
        addressIssuer = mock(BitcoinReceiveAddressIssuer.class);
        ledgerService = mock(BitcoinAccountLedgerService.class);
        taxEventService = mock(BitcoinTaxEventService.class);
        BitcoinAccountAuditService auditService = mock(BitcoinAccountAuditService.class);

        service = new ReceivingRequestService(
                requestRepository,
                addressRepository,
                cardRepository,
                accountRepository,
                accountService,
                addressIssuer,
                ledgerService,
                taxEventService,
                auditService,
                "regtest",
                2,
                6);
    }

    @Test
    void createReceiveRequestUsesConfiguredReadableRetention() {
        BitcoinAccountEntity account = new BitcoinAccountEntity();
        account.setUserId(42L);
        account.setType(BitcoinAccountEnums.AccountType.INTERNAL_CARD);
        account.setCustody(BitcoinAccountEnums.CustodyType.KEROSENE_CUSTODIAL);

        InternalBtcCardEntity card = new InternalBtcCardEntity();
        card.setBitcoinAccountId(account.getId());
        card.setLedgerAccountId(UUID.randomUUID());

        when(accountService.requireInternalCard(42L, account.getId())).thenReturn(card);
        when(addressIssuer.issue("btc-card:" + card.getId())).thenReturn(
                new BitcoinReceiveAddressIssuer.IssuedAddress(
                        "bcrt1qcreated0000000000000000000000000000000",
                        "m/84'/1'/0'/0/9",
                        9,
                        "TEST"));
        when(addressRepository.save(any(ReceivingAddressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(requestRepository.save(any(ReceivingRequestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<ReceivingRequestEntity> captor = ArgumentCaptor.forClass(ReceivingRequestEntity.class);

        service.create(42L, account.getId(), 12_000L, "15M", true);

        verify(requestRepository).save(captor.capture());
        ReceivingRequestEntity saved = captor.getValue();
        assertNotNull(saved.getPurgeAfter());
        assertTrue(saved.getPurgeAfter().isAfter(LocalDateTime.now().plusHours(5)));
        assertTrue(saved.getPurgeAfter().isBefore(LocalDateTime.now().plusHours(7)));
    }

    @Test
    void listForAccountReturnsVisibleRequestsAndRefreshesExpiredActiveRequests() {
        BitcoinAccountEntity account = new BitcoinAccountEntity();
        account.setUserId(42L);
        account.setType(BitcoinAccountEnums.AccountType.INTERNAL_CARD);
        account.setCustody(BitcoinAccountEnums.CustodyType.KEROSENE_CUSTODIAL);

        InternalBtcCardEntity card = new InternalBtcCardEntity();
        card.setBitcoinAccountId(account.getId());
        card.setLedgerAccountId(UUID.randomUUID());

        ReceivingAddressEntity address = new ReceivingAddressEntity();
        address.setCardId(card.getId());
        address.setAddress("bcrt1qhistory0000000000000000000000000");
        address.setDerivationPath("m/84'/1'/0'/0/7");
        address.setDerivationIndex(7);

        ReceivingRequestEntity request = new ReceivingRequestEntity();
        request.setCardId(card.getId());
        request.setAddressId(address.getId());
        request.setPublicCode("KRS-history");
        request.setAmountSats(21_000L);
        request.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(accountService.requireInternalCard(42L, account.getId())).thenReturn(card);
        when(requestRepository.findTop50ByCardIdAndStatusNotOrderByCreatedAtDesc(
                card.getId(), BitcoinAccountEnums.ReceivingRequestStatus.HIDDEN)).thenReturn(List.of(request));
        when(addressRepository.findById(address.getId())).thenReturn(Optional.of(address));

        List<Map<String, Object>> result = service.listForAccount(42L, account.getId());

        assertEquals(1, result.size());
        assertEquals(account.getId(), result.get(0).get("accountId"));
        assertEquals(address.getAddress(), result.get(0).get("address"));
        assertEquals(BitcoinAccountEnums.ReceivingRequestStatus.EXPIRED, result.get(0).get("status"));
        assertEquals(BitcoinAccountEnums.ReceivingRequestStatus.EXPIRED, request.getStatus());
        verify(requestRepository).saveAll(any());
    }

    @Test
    void observeOnchainPaymentMarksMempoolSeenBeforeConfirmations() {
        Fixture fixture = fixture(null, LocalDateTime.now().plusHours(1));
        LedgerEntryEntity entry = ledgerEntry(fixture.card.getLedgerAccountId());
        when(ledgerService.creditPending(
                eq(fixture.card.getLedgerAccountId()),
                eq(12_000L),
                eq("ONCHAIN_RECEIVE"),
                eq(fixture.request.getId().toString()),
                eq("tx123:0"))).thenReturn(entry);

        service.observeOnchainPayment(fixture.address.getAddress(), "tx123", 0, 12_000L, 0);

        assertEquals(BitcoinAccountEnums.ReceivingRequestStatus.MEMPOOL_SEEN, fixture.request.getStatus());
        verify(ledgerService, never()).makeAvailable(any(UUID.class));
        verify(taxEventService).recordTemporaryEvent(
                eq(fixture.account.getUserId()),
                eq(BitcoinAccountEnums.TaxEventType.DEPOSIT_INTERNAL),
                eq(12_000L),
                eq("tx123:0"),
                eq(fixture.account.getId()),
                eq(fixture.card.getId()),
                eq(null),
                eq("USER_CLASSIFICATION_PENDING"));
    }

    @Test
    void observeOnchainPaymentMakesLedgerAvailableAfterMinimumConfirmations() {
        Fixture fixture = fixture(12_000L, LocalDateTime.now().plusHours(1));
        LedgerEntryEntity entry = ledgerEntry(fixture.card.getLedgerAccountId());
        when(ledgerService.creditPending(
                eq(fixture.card.getLedgerAccountId()),
                eq(12_000L),
                eq("ONCHAIN_RECEIVE"),
                eq(fixture.request.getId().toString()),
                eq("tx123:1"))).thenReturn(entry);

        service.observeOnchainPayment(fixture.address.getAddress(), "tx123", 1, 12_000L, 2);

        assertEquals(BitcoinAccountEnums.ReceivingRequestStatus.PAID, fixture.request.getStatus());
        assertNotNull(fixture.request.getPaidAt());
        verify(ledgerService).makeAvailable(entry.getId());
    }

    @Test
    void expiredMismatchedPaymentRequiresSelfServiceWithAutoHold() {
        Fixture fixture = fixture(10_000L, LocalDateTime.now().minusMinutes(5));
        LedgerEntryEntity entry = ledgerEntry(fixture.card.getLedgerAccountId());
        when(ledgerService.creditPending(
                eq(fixture.card.getLedgerAccountId()),
                eq(12_000L),
                eq("ONCHAIN_RECEIVE"),
                eq(fixture.request.getId().toString()),
                eq("late:0"))).thenReturn(entry);

        service.observeOnchainPayment(fixture.address.getAddress(), "late", 0, 12_000L, 0);

        assertEquals(BitcoinAccountEnums.ReceivingRequestStatus.USER_ACTION_REQUIRED, fixture.request.getStatus());
        assertNotNull(fixture.request.getSelfServiceReason());
        verify(ledgerService).moveAvailableToAutoHoldByIdempotencyKey("late:0", "RECEIVE_USER_ACTION_REQUIRED");
        verify(ledgerService, never()).makeAvailable(any(UUID.class));
        verify(taxEventService).recordTemporaryEvent(
                eq(fixture.account.getUserId()),
                eq(BitcoinAccountEnums.TaxEventType.DEPOSIT_INTERNAL),
                eq(12_000L),
                eq("late:0"),
                eq(fixture.account.getId()),
                eq(fixture.card.getId()),
                eq(null),
                eq("USER_ACTION_REQUIRED"));
    }

    @Test
    void oneTimePaidLinkAdditionalPaymentRequiresSelfServiceAutoHold() {
        Fixture fixture = fixture(12_000L, LocalDateTime.now().plusHours(1));
        fixture.request.setStatus(BitcoinAccountEnums.ReceivingRequestStatus.PAID);
        LedgerEntryEntity entry = ledgerEntry(fixture.card.getLedgerAccountId());
        when(ledgerService.hasEntryForIdempotencyKey("second:0")).thenReturn(false);
        when(ledgerService.creditPending(
                eq(fixture.card.getLedgerAccountId()),
                eq(8_000L),
                eq("ONCHAIN_RECEIVE"),
                eq(fixture.request.getId().toString()),
                eq("second:0"))).thenReturn(entry);

        service.observeOnchainPayment(fixture.address.getAddress(), "second", 0, 8_000L, 3);

        assertEquals(BitcoinAccountEnums.ReceivingRequestStatus.USER_ACTION_REQUIRED, fixture.request.getStatus());
        assertNotNull(fixture.request.getSelfServiceReason());
        verify(ledgerService).moveAvailableToAutoHoldByIdempotencyKey("second:0", "RECEIVE_USER_ACTION_REQUIRED");
        verify(ledgerService, never()).makeAvailable(any(UUID.class));
    }

    @Test
    void confirmationRegressionMovesPaidDepositToAutoResolutionAndAutoHold() {
        Fixture fixture = fixture(12_000L, LocalDateTime.now().plusHours(1));
        fixture.request.setStatus(BitcoinAccountEnums.ReceivingRequestStatus.PAID);

        service.observeOnchainPayment(fixture.address.getAddress(), "tx123", 0, 12_000L, 0);

        assertEquals(BitcoinAccountEnums.ReceivingRequestStatus.AUTO_RESOLUTION_PENDING, fixture.request.getStatus());
        assertNotNull(fixture.request.getSelfServiceReason());
        verify(ledgerService).moveAvailableToAutoHoldByIdempotencyKey("tx123:0", "CONFIRMATION_REGRESSION");
        verify(ledgerService, never()).creditPending(
                any(UUID.class),
                anyLong(),
                any(),
                any(),
                any());
    }

    private Fixture fixture(Long expectedAmountSats, LocalDateTime expiresAt) {
        BitcoinAccountEntity account = new BitcoinAccountEntity();
        account.setUserId(42L);
        account.setType(BitcoinAccountEnums.AccountType.INTERNAL_CARD);
        account.setCustody(BitcoinAccountEnums.CustodyType.KEROSENE_CUSTODIAL);
        account.setLabel("Internal BTC Card");

        InternalBtcCardEntity card = new InternalBtcCardEntity();
        card.setBitcoinAccountId(account.getId());
        card.setLedgerAccountId(UUID.randomUUID());

        ReceivingAddressEntity address = new ReceivingAddressEntity();
        address.setCardId(card.getId());
        address.setAddress("bcrt1qtestaddress000000000000000000000");
        address.setDerivationPath("m/84'/1'/0'/0/0");
        address.setDerivationIndex(0);
        address.setStatus(BitcoinAccountEnums.ReceivingAddressStatus.ASSIGNED);

        ReceivingRequestEntity request = new ReceivingRequestEntity();
        request.setCardId(card.getId());
        request.setAddressId(address.getId());
        request.setPublicCode("KRS-test");
        request.setAmountSats(expectedAmountSats);
        request.setExpiresAt(expiresAt);

        when(addressRepository.findByAddress(address.getAddress())).thenReturn(Optional.of(address));
        when(requestRepository.findTopByAddressIdOrderByCreatedAtDesc(address.getId())).thenReturn(Optional.of(request));
        when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(addressRepository.save(any(ReceivingAddressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(requestRepository.save(any(ReceivingRequestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        return new Fixture(account, card, address, request);
    }

    private LedgerEntryEntity ledgerEntry(UUID ledgerAccountId) {
        LedgerEntryEntity entry = new LedgerEntryEntity();
        entry.setLedgerAccountId(ledgerAccountId);
        entry.setDirection(BitcoinAccountEnums.LedgerDirection.CREDIT);
        entry.setStatus(BitcoinAccountEnums.LedgerEntryStatus.PENDING);
        entry.setAmountSats(12_000L);
        entry.setSourceType("ONCHAIN_RECEIVE");
        entry.setSourceId(UUID.randomUUID().toString());
        entry.setIdempotencyKey("tx123:0");
        return entry;
    }

    private record Fixture(
            BitcoinAccountEntity account,
            InternalBtcCardEntity card,
            ReceivingAddressEntity address,
            ReceivingRequestEntity request) {
    }
}
