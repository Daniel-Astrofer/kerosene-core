package source.treasury.application.port.out;

public interface BlockchainReservePort {

    long getHotWalletBalance();

    long getConfirmedBalanceForXpub(String xpub, int scanRange, boolean includeChange);

    long getConfirmedBalanceForAddress(String address);
}
