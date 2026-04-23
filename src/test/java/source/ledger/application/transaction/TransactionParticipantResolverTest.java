package source.ledger.application.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.auth.application.service.account.AccountActivationService;
import source.auth.application.service.user.UserService;
import source.auth.model.entity.UserDataBase;
import source.common.service.AddressDerivationService;
import source.ledger.application.paymentrequest.PaymentRequestDestinationHashService;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletContract;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Transaction Participant Resolver")
class TransactionParticipantResolverTest {

    @Mock
    private WalletContract walletService;

    @Mock
    private UserService userService;

    @Mock
    private AccountActivationService accountActivationService;

    @Mock
    private PaymentRequestDestinationHashService destinationHashService;

    @Mock
    private AddressDerivationService addressDerivationService;

    private TransactionParticipantResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TransactionParticipantResolver(
                walletService,
                userService,
                accountActivationService,
                destinationHashService,
                addressDerivationService);
    }

    @Test
    @DisplayName("Should resolve receiver by username")
    void shouldResolveReceiverByUsername() throws Exception {
        UserDataBase receiver = user(20L, "receiver-user");
        WalletEntity wallet = wallet(101L, receiver, "RECEIVER", "tb1qreceiver000000000000000000000000000000000");

        when(userService.findByUsername("receiver-user")).thenReturn(receiver);
        when(walletService.findByUserId(20L)).thenReturn(List.of(wallet));

        WalletEntity resolved = resolver.resolveReceiverWallet("receiver-user");

        assertSame(wallet, resolved);
        verify(accountActivationService).assertInboundEnabled(receiver);
    }

    @Test
    @DisplayName("Should resolve receiver by public destination hash")
    void shouldResolveReceiverByDestinationHash() throws Exception {
        UserDataBase receiver = user(21L, "receiver-hash");
        WalletEntity wallet = wallet(102L, receiver, "HASH", "tb1qhash0000000000000000000000000000000000000");
        String destinationHash = "9f4a7b7bb6d8a1c0f1a3dcb54036bf2f3d4cf53d4c0f5cc7df0e8c1a9d1b2c3d";

        when(walletService.findAll()).thenReturn(List.of(wallet));
        when(destinationHashService.buildDestinationHash(wallet)).thenReturn(destinationHash);

        WalletEntity resolved = resolver.resolveReceiverWallet(destinationHash);

        assertSame(wallet, resolved);
        verify(accountActivationService).assertInboundEnabled(receiver);
    }

    @Test
    @DisplayName("Should resolve receiver by derived blockchain address when no address is persisted")
    void shouldResolveReceiverByDerivedBlockchainAddress() throws Exception {
        UserDataBase receiver = user(22L, "receiver-derived");
        WalletEntity wallet = wallet(103L, receiver, "DERIVED", null);
        wallet.setPassphraseHash("$argon2id$v=19$m=65536,t=3,p=4$receiver$hash");

        String derivedAddress = "tb1qderived00000000000000000000000000000000000";

        when(walletService.findByDepositAddress(derivedAddress)).thenReturn(null);
        when(walletService.findAll()).thenReturn(List.of(wallet));
        when(addressDerivationService.deriveAddress(103L, wallet.getPassphraseHash())).thenReturn(derivedAddress);

        WalletEntity resolved = resolver.resolveReceiverWallet(derivedAddress);

        assertSame(wallet, resolved);
        verify(accountActivationService).assertInboundEnabled(receiver);
    }

    private UserDataBase user(Long id, String username) throws Exception {
        UserDataBase user = new UserDataBase();
        java.lang.reflect.Field field = UserDataBase.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
        user.setUsername(username);
        return user;
    }

    private WalletEntity wallet(Long id, UserDataBase user, String name, String depositAddress) {
        WalletEntity wallet = new WalletEntity();
        wallet.setId(id);
        wallet.setUser(user);
        wallet.setName(name);
        wallet.setDepositAddress(depositAddress);
        wallet.setPassphraseHash("$argon2id$mock");
        return wallet;
    }
}
