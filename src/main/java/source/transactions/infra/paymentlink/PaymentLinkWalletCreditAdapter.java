package source.transactions.infra.paymentlink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import source.ledger.service.LedgerService;
import source.transactions.application.paymentlink.PaymentLinkCreditPort;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.exception.PaymentLinkExceptions;
import source.wallet.application.port.in.WalletLookupPort;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletCardProfileService;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PaymentLinkWalletCreditAdapter implements PaymentLinkCreditPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentLinkWalletCreditAdapter.class);

    private final WalletLookupPort walletLookupPort;
    private final LedgerService ledgerService;
    private final WalletCardProfileService walletCardProfileService;

    public PaymentLinkWalletCreditAdapter(
            WalletLookupPort walletLookupPort,
            LedgerService ledgerService,
            WalletCardProfileService walletCardProfileService) {
        this.walletLookupPort = walletLookupPort;
        this.ledgerService = ledgerService;
        this.walletCardProfileService = walletCardProfileService;
    }

    @Override
    public void creditUserWallet(PaymentLinkDTO paymentLink) {
        if (paymentLink.getUserId() == null) {
            throw new PaymentLinkExceptions.PaymentLinkCreditFailed(
                    "Payment link is not associated with a persisted user.");
        }

        WalletEntity wallet = walletLookupPort.findPrimaryWallet(paymentLink.getUserId());
        if (wallet == null) {
            throw new PaymentLinkExceptions.PaymentLinkCreditFailed(
                    "Usuario " + paymentLink.getUserId() + " nao tem wallet para receber o pagamento.");
        }

        BigDecimal depositFee = walletCardProfileService.calculateDepositFee(paymentLink.getUserId(), paymentLink.getAmountBtc());
        BigDecimal netAmount = paymentLink.getAmountBtc()
                .subtract(depositFee)
                .setScale(8, RoundingMode.HALF_UP);

        if (netAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentLinkExceptions.PaymentLinkCreditFailed(
                    "Configured deposit fee consumes the entire payment link amount.");
        }

        paymentLink.setGrossAmountBtc(paymentLink.getAmountBtc());
        paymentLink.setDepositFeeBtc(depositFee);
        paymentLink.setNetAmountBtc(netAmount);
        ledgerService.updateBalance(wallet.getId(), netAmount, "PAYMENT_LINK_" + paymentLink.getId());
        log.info("Credited payment link {} to wallet {} with net amount {}", paymentLink.getId(), wallet.getId(),
                netAmount);
    }
}
