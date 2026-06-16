package source.mining.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import source.auth.application.service.identityaccess.TransactionalAuthenticationPort;
import source.auth.application.service.identityaccess.TransactionalAuthenticationRequest;
import source.auth.application.service.identityaccess.TransactionalAuthenticationResult;
import source.auth.model.entity.UserDataBase;
import source.kfe.model.KfeBalanceEntity;
import source.kfe.model.KfeWalletAddressEntity;
import source.kfe.model.KfeWalletEntity;
import source.kfe.model.KfeWalletKind;
import source.kfe.model.KfeWalletStatus;
import source.kfe.repository.KfeWalletAddressRepository;
import source.kfe.repository.KfeWalletRepository;
import source.kfe.service.KfeBalanceService;
import source.mining.dto.MiningAllocationRequestDTO;
import source.mining.dto.MiningAllocationResponseDTO;
import source.mining.entity.MiningAllocationEntity;
import source.mining.entity.MiningRigOfferEntity;
import source.mining.repository.MiningAllocationRepository;
import source.mining.repository.MiningRigOfferRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MiningServiceTest {

    @Mock
    private MiningRigOfferRepository rigOfferRepository;

    @Mock
    private MiningAllocationRepository allocationRepository;

    @Mock
    private KfeWalletRepository walletRepository;

    @Mock
    private KfeWalletAddressRepository walletAddressRepository;

    @Mock
    private KfeBalanceService balanceService;

    @Mock
    private MiningHistoryPort historyPort;

    @Mock
    private TransactionalAuthenticationPort transactionalAuthenticationPort;

    @Mock
    private source.notification.service.NotificationService notificationService;

    private MiningService service;

    @BeforeEach
    void setUp() {
        RigCatalog rigCatalog = new RigCatalog(rigOfferRepository);
        MiningSettlementService settlementService = new MiningSettlementService(
                balanceService,
                rigCatalog,
                allocationRepository,
                historyPort,
                notificationService);
        MiningAllocationUseCase allocationUseCase = new MiningAllocationUseCase(
                allocationRepository,
                walletRepository,
                walletAddressRepository,
                transactionalAuthenticationPort,
                rigCatalog,
                settlementService,
                historyPort,
                notificationService);
        service = new MiningService(rigCatalog, allocationUseCase);
    }

    @Test
    void createAllocationCalculatesHashrateFromBudget() {
        UserDataBase user = mock(UserDataBase.class);
        when(user.getId()).thenReturn(1L);
        when(user.getUsername()).thenReturn("miner");

        UUID walletId = UUID.randomUUID();
        KfeWalletEntity wallet = new KfeWalletEntity();
        wallet.setId(walletId);
        wallet.setUserId(1L);
        wallet.setLabel("TREASURY");
        wallet.setKind(KfeWalletKind.INTERNAL);
        wallet.setStatus(KfeWalletStatus.ACTIVE);
        wallet.setSpendable(true);

        KfeWalletAddressEntity address = new KfeWalletAddressEntity();
        address.setWalletId(walletId);
        address.setAddress("bc1qmineraddressxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

        KfeBalanceEntity balance = KfeBalanceEntity.empty(walletId, "BTC", "initial-hash");
        balance.setAvailableSats(100_000_000L);

        MiningRigOfferEntity rig = new MiningRigOfferEntity();
        rig.setId(21L);
        rig.setRigCode("sha256-pro-150");
        rig.setDisplayName("Pro SHA256 150TH");
        rig.setAlgorithm("SHA256");
        rig.setHashUnit("TH");
        rig.setPricePerUnitDayBtc(new BigDecimal("0.00001000"));
        rig.setProjectedBtcYieldPerUnitDay(new BigDecimal("0.00000800"));
        rig.setProjectedYieldMultiplier(new BigDecimal("0.98000000"));
        rig.setAvailableHashrate(new BigDecimal("5000.00000000"));
        rig.setMinRentalHours(1);
        rig.setMaxRentalHours(168);
        rig.setProvider("KEROSENE_INTERNAL");
        rig.setActive(true);

        when(rigOfferRepository.findByIdAndActiveTrue(21L)).thenReturn(java.util.Optional.of(rig));
        when(rigOfferRepository.save(any(MiningRigOfferEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(walletRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(wallet));
        when(walletAddressRepository.findTopByWalletIdAndStatusOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(address));
        when(balanceService.requireForUpdate(walletId, "BTC")).thenReturn(balance);
        when(transactionalAuthenticationPort.authorize(any(TransactionalAuthenticationRequest.class)))
                .thenReturn(new TransactionalAuthenticationResult(user, ""));
        when(allocationRepository.save(any(MiningAllocationEntity.class))).thenAnswer(invocation -> {
            MiningAllocationEntity entity = invocation.getArgument(0);
            if (entity.getStartsAt() == null) {
                entity.setStartsAt(LocalDateTime.now());
            }
            if (entity.getEndsAt() == null) {
                entity.setEndsAt(LocalDateTime.now().plusHours(24));
            }
            return entity;
        });

        MiningAllocationResponseDTO response = service.createAllocation(
                1L,
                new MiningAllocationRequestDTO(
                        "TREASURY",
                        21L,
                        null,
                        new BigDecimal("0.01000000"),
                        24,
                        null,
                        "stratum+tcp://pool.example:3333",
                        "worker.01",
                        "123456",
                        null,
                        "pass"));

        assertEquals(new BigDecimal("1000.00000000"), response.allocatedHashrate());
        assertEquals(new BigDecimal("0.01000000"), response.rentalCostBtc());
        assertEquals("ACTIVE", response.status());
        verify(balanceService).reserve(walletId, "BTC", 1_000_000L);
        verify(balanceService).settleReservedDebit(walletId, "BTC", 1_000_000L);
    }
}
