package source.wallet.application.port.in;

public interface WalletAddressIndexPort {

    int incrementLastDerivedIndex(Long walletId);
}
