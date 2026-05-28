package source.transactions.infra.paymentlink;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.ledger.service.LedgerService;
import source.transactions.dto.PaymentLinkDTO;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletCardProfileService;
import source.wallet.service.WalletService;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentLinkWalletCreditAdapterTest {

    @Mock
    private WalletService walletService;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private WalletCardProfileService walletCardProfileService;

    @InjectMocks
    private PaymentLinkWalletCreditAdapter paymentLinkWalletCreditAdapter;

    @Test
    void shouldCreditNetAmountAfterDepositFee() {
        PaymentLinkDTO paymentLink = new PaymentLinkDTO();
        paymentLink.setId("pay-4");
        paymentLink.setUserId(8L);
        paymentLink.setAmountBtc(new BigDecimal("1.00000000"));
        paymentLink.setTxid("tx-payment-link-4");

        WalletEntity wallet = new WalletEntity();
        wallet.setId(55L);

        when(walletService.findPrimaryWallet(8L)).thenReturn(wallet);
        when(walletCardProfileService.calculateDepositFee(8L, new BigDecimal("1.00000000")))
                .thenReturn(new BigDecimal("0.00900000"));

        paymentLinkWalletCreditAdapter.creditUserWallet(paymentLink);

        assertEquals(new BigDecimal("1.00000000"), paymentLink.getGrossAmountBtc());
        assertEquals(new BigDecimal("0.00900000"), paymentLink.getDepositFeeBtc());
        assertEquals(new BigDecimal("0.99100000"), paymentLink.getNetAmountBtc());
        verify(ledgerService).updateBalance(
                eq(55L),
                eq(new BigDecimal("0.99100000")),
                eq("PAYMENT_LINK_CREDIT:paymentLink=pay-4:txid=sha256:bb0e456ce8101095"));
    }
}
