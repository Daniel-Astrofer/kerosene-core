package source.transactions.application.externalpayments;

import source.wallet.model.WalletEntity;

public interface ExternalPaymentsWalletPort {

    WalletEntity requireWallet(Long userId, String walletName);

    WalletEntity save(WalletEntity wallet);

    int incrementLastDerivedIndex(Long walletId);

    String deriveAddressFromXpub(String xpub, int index);

    String deriveAddress(Long walletId, String passphraseHash);
}
