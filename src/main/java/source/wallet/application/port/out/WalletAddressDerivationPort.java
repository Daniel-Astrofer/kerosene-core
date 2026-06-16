package source.wallet.application.port.out;

public interface WalletAddressDerivationPort {

    String deriveAddressFromXpub(String xpub, int index);
}
