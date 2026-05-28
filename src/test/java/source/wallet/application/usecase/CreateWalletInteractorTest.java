package source.wallet.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.auth.model.entity.UserDataBase;
import source.wallet.application.handler.create.AllocateWalletAddressHandler;
import source.wallet.application.handler.create.CreateWalletLedgerHandler;
import source.wallet.application.handler.create.InstantiateWalletHandler;
import source.wallet.application.handler.create.LoadWalletUserHandler;
import source.wallet.application.handler.create.PersistNewWalletHandler;
import source.wallet.application.handler.create.ValidateCreateWalletRequestHandler;
import source.wallet.application.port.out.WalletAddressProvisionPort;
import source.wallet.application.port.out.WalletCardProfilePort;
import source.wallet.application.port.out.WalletCredentialsPort;
import source.wallet.application.port.out.WalletLedgerPort;
import source.wallet.application.port.out.WalletUserPort;
import source.wallet.application.service.WalletPersistenceSupport;
import source.wallet.application.service.WalletReader;
import source.wallet.application.service.WalletResponseAssembler;
import source.wallet.dto.WalletRequestDTO;
import source.wallet.dto.WalletResponseDTO;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletCardProfile;
import source.wallet.service.WalletCardLifecycleService;
import source.wallet.service.WalletCardSnapshot;
import source.wallet.service.WalletCardType;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateWalletInteractor Tests")
class CreateWalletInteractorTest {

    @Mock
    private WalletUserPort walletUserPort;
    @Mock
    private WalletReader walletReader;
    @Mock
    private WalletCredentialsPort walletCredentialsPort;
    @Mock
    private WalletPersistenceSupport walletPersistenceSupport;
    @Mock
    private WalletAddressProvisionPort walletAddressProvisionPort;
    @Mock
    private WalletLedgerPort walletLedgerPort;
    @Mock
    private WalletCardProfilePort walletCardProfilePort;
    @Mock
    private WalletCardLifecycleService walletCardLifecycleService;

    private CreateWalletInteractor createWalletInteractor;

    @BeforeEach
    void setUp() {
        createWalletInteractor = new CreateWalletInteractor(
                new LoadWalletUserHandler(walletUserPort),
                new ValidateCreateWalletRequestHandler(walletReader, walletCredentialsPort),
                new InstantiateWalletHandler(walletCredentialsPort),
                new PersistNewWalletHandler(walletPersistenceSupport),
                new AllocateWalletAddressHandler(walletAddressProvisionPort),
                new CreateWalletLedgerHandler(walletLedgerPort),
                walletCardProfilePort,
                walletCredentialsPort,
                new WalletResponseAssembler(),
                walletCardLifecycleService);
    }

    @Test
    void createWalletBuildsAndPersistsThroughHandlerChain() {
        UserDataBase user = new UserDataBase();
        user.setUsername("alice");
        WalletCardProfile profile = new WalletCardProfile(
                WalletCardType.BRONZE,
                new BigDecimal("0.0090"),
                new BigDecimal("0.0090"),
                BigDecimal.ZERO.setScale(8));
        WalletCardSnapshot cardSnapshot = new WalletCardSnapshot(
                "TESTWALLET",
                "5300 0000 **** 4242",
                "4242",
                1,
                "ACTIVE",
                null,
                null,
                null,
                null,
                null,
                null);

        when(walletUserPort.requireUser(1L)).thenReturn(user);
        when(walletReader.existsByUserIdAndName(1L, "TESTWALLET")).thenReturn(false);
        when(walletCredentialsPort.generateTotpSecret()).thenReturn("BASE32SECRET");
        when(walletCredentialsPort.buildWalletTotpUri("TESTWALLET", "BASE32SECRET")).thenReturn("otpauth://wallet");
        when(walletCardProfilePort.resolveProfile(1L)).thenReturn(profile);
        when(walletCardLifecycleService.resolve(any(WalletEntity.class))).thenReturn(cardSnapshot);
        when(walletPersistenceSupport.persistNew(any(WalletEntity.class))).thenAnswer(invocation -> {
            WalletEntity wallet = invocation.getArgument(0);
            wallet.setId(77L);
            return wallet;
        });
        when(walletAddressProvisionPort.allocate(eq(1L), any(WalletEntity.class), eq("wallet-create:TESTWALLET"), eq(false)))
                .thenAnswer(invocation -> {
                    WalletEntity wallet = invocation.getArgument(1);
                    wallet.setDepositAddress("bc1qderivedaddress");
                    return new WalletAddressProvisionPort.Allocation(
                            "bc1qderivedaddress",
                            "XPUB_INDEX_0",
                            "KEROSENE_LOCAL",
                            false);
                });

        WalletResponseDTO response = createWalletInteractor.createWallet(
                new WalletRequestDTO("management-secret", "TestWallet", "xpub661Example", "SELF_CUSTODY"),
                1L);

        assertEquals(77L, response.id());
        assertEquals("TESTWALLET", response.name());
        assertEquals("otpauth://wallet", response.totpUri());
        assertEquals("bc1qderivedaddress", response.depositAddress());
        assertEquals("SELF_CUSTODY", response.walletMode());
        assertTrue(response.xpubConfigured());
        verify(walletLedgerPort).createLedger(any(WalletEntity.class), eq("Initial ledger for new wallet"));
    }

    @Test
    void createWalletRejectsDuplicateWalletName() {
        when(walletUserPort.requireUser(1L)).thenReturn(new UserDataBase());
        when(walletReader.existsByUserIdAndName(1L, "TESTWALLET")).thenReturn(true);

        assertThrows(
                WalletExceptions.WalletNameAlreadyExists.class,
                () -> createWalletInteractor.createWallet(
                        new WalletRequestDTO("test-passphrase-bip39", "TestWallet", null, "KEROSENE"),
                        1L));
    }
}
