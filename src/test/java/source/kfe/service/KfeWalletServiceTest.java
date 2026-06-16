package source.kfe.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import source.common.service.AddressDerivationService;
import source.kfe.dto.KfeCreateWalletRequest;
import source.kfe.dto.KfeWalletResponse;
import source.kfe.model.KfeWalletEntity;
import source.kfe.model.KfeWalletKind;
import source.kfe.model.KfeWalletStatus;
import source.kfe.repository.KfeWalletAddressRepository;
import source.kfe.repository.KfeWalletRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KfeWalletServiceTest {

    @Mock
    private KfeWalletRepository walletRepository;

    @Mock
    private KfeWalletAddressRepository addressRepository;

    @Mock
    private KfeBalanceService balanceService;

    @Mock
    private KfeHashService hashService;

    @Mock
    private KfeAuditLogService auditLogService;

    @Mock
    private KfeQuorumGateway quorumGateway;

    @Mock
    private KfeMpcKeyService mpcKeyService;

    @Mock
    private KfeResponseMapper responseMapper;

    @Mock
    private KfeDashboardPublisher dashboardPublisher;

    @Mock
    private AddressDerivationService addressDerivationService;

    @Mock
    private KfeReceiveAddressIssuer receiveAddressIssuer;

    @Mock
    private TransactionTemplate transactionTemplate;

    private KfeWalletService service;

    @BeforeEach
    void setUp() {
        service = new KfeWalletService(
                walletRepository,
                addressRepository,
                balanceService,
                hashService,
                auditLogService,
                quorumGateway,
                mpcKeyService,
                responseMapper,
                dashboardPublisher,
                addressDerivationService,
                receiveAddressIssuer,
                transactionTemplate
        );
    }

    @Test
    void listWalletsReturnsMappedResponses() {
        KfeWalletEntity wallet = new KfeWalletEntity();
        wallet.setId(UUID.randomUUID());
        wallet.setUserId(1L);

        when(walletRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(wallet));
        when(responseMapper.toWalletResponse(wallet)).thenReturn(new KfeWalletResponse(
                wallet.getId(), KfeWalletKind.CUSTODIAL_ONCHAIN, KfeWalletStatus.ACTIVE, "label", "BTC",
                true, true, true, "xpub", java.time.LocalDateTime.now(), java.time.LocalDateTime.now()
        ));

        List<KfeWalletResponse> responses = service.listWallets(1L);

        assertEquals(1, responses.size());
        verify(walletRepository).findByUserIdOrderByCreatedAtDesc(1L);
        verify(responseMapper).toWalletResponse(wallet);
    }

}
