package source.ledger.orchestrator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import source.auth.application.service.user.UserService;
import source.auth.model.entity.UserDataBase;
import source.ledger.dto.TransactionDTO;
import source.ledger.entity.LedgerEntity;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.service.LedgerContract;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletContract;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Transaction Orchestrator Tests")
class TransactionTest {

        @Mock
        private WalletContract walletService;

        @Mock
        private LedgerContract ledgerService;

        @Mock
        private UserService userService;

        @Mock
        private SecurityContext securityContext;

        @Mock
        private Authentication authentication;

        @InjectMocks
        private Transaction transaction;

        private TransactionDTO transactionDTO;
        private UserDataBase sender;
        private UserDataBase receiver;
        private WalletEntity senderWallet;
        private WalletEntity receiverWallet;
        private LedgerEntity senderLedger;
        private LedgerEntity receiverLedger;

        @BeforeEach
        void setUp() throws Exception {
                sender = new UserDataBase();
                setPrivateId(sender, 1L);
                sender.setUsername("sender");

                receiver = new UserDataBase();
                setPrivateId(receiver, 2L);
                receiver.setUsername("receiver");

                senderWallet = new WalletEntity();
                senderWallet.setId(1L);
                senderWallet.setName("SenderWallet");
                senderWallet.setPassphraseHash("sender-addr");
                senderWallet.setUser(sender);

                receiverWallet = new WalletEntity();
                receiverWallet.setId(2L);
                receiverWallet.setName("ReceiverWallet");
                receiverWallet.setPassphraseHash("receiver-addr");
                receiverWallet.setUser(receiver);

                senderLedger = new LedgerEntity(senderWallet, "Sender ledger");
                senderLedger.setId(1);
                senderLedger.setBalance(new BigDecimal("500.00"));

                receiverLedger = new LedgerEntity(receiverWallet, "Receiver ledger");
                receiverLedger.setId(2);
                receiverLedger.setBalance(new BigDecimal("100.00"));

                transactionDTO = new TransactionDTO();
                transactionDTO.setSender("sender");
                transactionDTO.setReceiver("receiver");
                transactionDTO.setAmount(new BigDecimal("50.00"));
                transactionDTO.setContext("Payment for services");

                // Setup security context
                SecurityContextHolder.setContext(securityContext);
        }

        private void setPrivateId(Object target, Long id) throws Exception {
                java.lang.reflect.Field field = target.getClass().getDeclaredField("id");
                field.setAccessible(true);
                field.set(target, id);
        }

        @Test
        @DisplayName("Should process transaction successfully")
        void shouldProcessTransactionSuccessfully() {
                when(securityContext.getAuthentication()).thenReturn(authentication);
                when(authentication.getName()).thenReturn("1");
                when(userService.buscarPorId(1L)).thenReturn(Optional.of(sender));
                when(walletService.findByUserId(1L)).thenReturn(java.util.List.of(senderWallet));
                when(userService.findByUsername("receiver")).thenReturn(receiver);
                when(walletService.findByUserId(2L)).thenReturn(java.util.List.of(receiverWallet));

                when(ledgerService.findByWalletId(anyLong())).thenReturn(senderLedger);
                when(ledgerService.updateBalance(anyLong(), any(BigDecimal.class), anyString()))
                                .thenReturn(receiverLedger);

                assertDoesNotThrow(() -> transaction.processTransaction(transactionDTO));

                verify(ledgerService, times(2)).updateBalance(anyLong(), any(BigDecimal.class), anyString());
        }

        @Test
        @DisplayName("Should throw exception when receiver not found")
        void shouldThrowExceptionWhenReceiverNotFound() {
                when(securityContext.getAuthentication()).thenReturn(authentication);
                when(authentication.getName()).thenReturn("1");
                when(userService.buscarPorId(1L)).thenReturn(Optional.of(sender));
                when(walletService.findByUserId(1L)).thenReturn(java.util.List.of(senderWallet));
                when(userService.findByUsername("receiver")).thenReturn(null);

                assertThrows(LedgerExceptions.LedgerNotFoundException.class, () -> {
                        transaction.processTransaction(transactionDTO);
                });

                verify(ledgerService, never()).updateBalance(anyLong(), any(BigDecimal.class), anyString());
        }

        @Test
        @DisplayName("Should use default context when not provided")
        void shouldUseDefaultContextWhenNotProvided() {
                transactionDTO.setContext(null);

                when(securityContext.getAuthentication()).thenReturn(authentication);
                when(authentication.getName()).thenReturn("1");
                when(userService.buscarPorId(1L)).thenReturn(Optional.of(sender));
                when(walletService.findByUserId(1L)).thenReturn(java.util.List.of(senderWallet));
                when(userService.findByUsername("receiver")).thenReturn(receiver);
                when(walletService.findByUserId(2L)).thenReturn(java.util.List.of(receiverWallet));
                when(ledgerService.findByWalletId(anyLong())).thenReturn(senderLedger);

                assertDoesNotThrow(() -> transaction.processTransaction(transactionDTO));

                verify(ledgerService).updateBalance(anyLong(), any(BigDecimal.class), eq("Debit transaction"));
        }

        @Test
        @DisplayName("Should negate amount for debit operation")
        void shouldNegateAmountForDebitOperation() {
                when(securityContext.getAuthentication()).thenReturn(authentication);
                when(authentication.getName()).thenReturn("1");
                when(userService.buscarPorId(1L)).thenReturn(Optional.of(sender));
                when(walletService.findByUserId(1L)).thenReturn(java.util.List.of(senderWallet));
                when(userService.findByUsername("receiver")).thenReturn(receiver);
                when(walletService.findByUserId(2L)).thenReturn(java.util.List.of(receiverWallet));
                when(ledgerService.findByWalletId(anyLong())).thenReturn(senderLedger);

                transaction.processTransaction(transactionDTO);

                verify(ledgerService).updateBalance(
                                eq(senderWallet.getId()),
                                argThat(amount -> amount.compareTo(transactionDTO.getAmount().negate()) == 0),
                                anyString());
        }
}
