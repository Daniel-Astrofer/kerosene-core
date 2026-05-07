package source.ledger.application.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.auth.AuthExceptions;
import source.auth.application.service.account.AccountActivationService;
import source.auth.application.service.user.UserService;
import source.auth.model.entity.UserDataBase;
import source.ledger.exceptions.LedgerExceptions;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletContract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

    private TransactionParticipantResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TransactionParticipantResolver(
                walletService,
                userService,
                accountActivationService);
    }

    @Test
    @DisplayName("Should resolve receiver by username")
    void shouldResolveReceiverByUsername() throws Exception {
        UserDataBase receiver = user(20L, "receiver-user");
        WalletEntity wallet = wallet(101L, receiver, "RECEIVER", "tb1qreceiver000000000000000000000000000000000");

        when(userService.findByUsername("receiver-user")).thenReturn(receiver);
        when(walletService.findPrimaryWallet(20L)).thenReturn(wallet);

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

        when(walletService.findByDestinationHash(destinationHash)).thenReturn(wallet);

        WalletEntity resolved = resolver.resolveReceiverWallet(destinationHash);

        assertSame(wallet, resolved);
        verify(accountActivationService).assertInboundEnabled(receiver);
        verify(walletService, never()).findAll();
    }

    @Test
    @DisplayName("Should not scan all wallets to resolve derived blockchain address")
    void shouldNotScanAllWalletsForDerivedBlockchainAddress() throws Exception {
        UserDataBase receiver = user(22L, "receiver-derived");
        WalletEntity wallet = wallet(103L, receiver, "DERIVED", null);
        wallet.setPassphraseHash("$argon2id$v=19$m=65536,t=3,p=4$receiver$hash");

        String derivedAddress = "tb1qderived00000000000000000000000000000000000";

        when(walletService.findByDepositAddress(derivedAddress)).thenReturn(null);

        assertThrows(LedgerExceptions.ReceiverNotFoundException.class,
                () -> resolver.resolveReceiverWallet(derivedAddress));

        verify(walletService, never()).findAll();
    }

    @Test
    @DisplayName("Should report receiver not ready when username exists without wallet")
    void shouldReportReceiverNotReadyWhenUserHasNoWallet() throws Exception {
        UserDataBase receiver = user(23L, "receiver-nowallet");

        when(userService.findByUsername("receiver-nowallet")).thenReturn(receiver);
        when(walletService.findPrimaryWallet(23L)).thenReturn(null);

        LedgerExceptions.ReceiverNotReadyException exception = assertThrows(
                LedgerExceptions.ReceiverNotReadyException.class,
                () -> resolver.resolveReceiverWallet("receiver-nowallet"));

        assertEquals(
                "The destination user exists but does not yet have a wallet ready to receive funds.",
                exception.getMessage());
        assertEquals(LedgerExceptions.ReceiverNotReadyException.Reason.NO_RECEIVING_WALLET, exception.getReason());
        verify(accountActivationService, never()).assertInboundEnabled(any(UserDataBase.class));
    }

    @Test
    @DisplayName("Should report receiver not ready when destination exists but inbound is blocked")
    void shouldReportReceiverNotReadyWhenDestinationInboundIsBlocked() throws Exception {
        UserDataBase receiver = user(24L, "receiver-blocked");
        WalletEntity wallet = wallet(104L, receiver, "BLOCKED", "tb1qblocked000000000000000000000000000000000");

        when(walletService.findByDepositAddress(wallet.getDepositAddress())).thenReturn(wallet);
        doThrow(new AuthExceptions.InboundReceivingBlockedException("blocked"))
                .when(accountActivationService).assertInboundEnabled(eq(receiver));

        LedgerExceptions.ReceiverNotReadyException exception = assertThrows(
                LedgerExceptions.ReceiverNotReadyException.class,
                () -> resolver.resolveReceiverWallet(wallet.getDepositAddress()));

        assertEquals(
                "The destination user exists but is not yet ready to receive funds.",
                exception.getMessage());
        assertEquals(LedgerExceptions.ReceiverNotReadyException.Reason.INBOUND_BLOCKED, exception.getReason());
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
