package source.transactions.infra.paymentlink;

import org.springframework.stereotype.Component;
import source.transactions.application.paymentlink.PaymentLinkWalletPort;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletService;

@Component
public class PaymentLinkWalletAdapter implements PaymentLinkWalletPort {

    private final WalletService walletService;

    public PaymentLinkWalletAdapter(WalletService walletService) {
        this.walletService = walletService;
    }

    @Override
    public WalletEntity findPrimaryWallet(Long userId) {
        return walletService.findPrimaryWallet(userId);
    }
}
