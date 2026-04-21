package source.transactions.application.paymentlink;

import source.wallet.model.WalletEntity;

public interface PaymentLinkWalletPort {

    WalletEntity findPrimaryWallet(Long userId);
}
