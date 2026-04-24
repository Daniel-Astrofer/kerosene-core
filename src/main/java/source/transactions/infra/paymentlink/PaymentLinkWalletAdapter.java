package source.transactions.infra.paymentlink;

import org.springframework.stereotype.Component;
import source.transactions.application.paymentlink.PaymentLinkWalletPort;
import source.wallet.application.port.in.WalletLookupPort;
import source.wallet.model.WalletEntity;

@Component
public class PaymentLinkWalletAdapter implements PaymentLinkWalletPort {

    private final WalletLookupPort walletLookupPort;

    public PaymentLinkWalletAdapter(WalletLookupPort walletLookupPort) {
        this.walletLookupPort = walletLookupPort;
    }

    @Override
    public WalletEntity findPrimaryWallet(Long userId) {
        return walletLookupPort.findPrimaryWallet(userId);
    }
}
