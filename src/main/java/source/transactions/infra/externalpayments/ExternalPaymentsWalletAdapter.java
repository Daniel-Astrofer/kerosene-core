package source.transactions.infra.externalpayments;

import org.springframework.stereotype.Component;
import source.common.service.AddressDerivationService;
import source.transactions.application.externalpayments.ExternalPaymentsWalletPort;
import source.wallet.application.port.in.WalletAddressIndexPort;
import source.wallet.application.port.in.WalletLookupPort;
import source.wallet.application.service.WalletPersistenceSupport;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;

@Component
public class ExternalPaymentsWalletAdapter implements ExternalPaymentsWalletPort {

    private final WalletLookupPort walletLookupPort;
    private final WalletAddressIndexPort walletAddressIndexPort;
    private final WalletPersistenceSupport walletPersistenceSupport;
    private final AddressDerivationService addressDerivationService;

    public ExternalPaymentsWalletAdapter(
            WalletLookupPort walletLookupPort,
            WalletAddressIndexPort walletAddressIndexPort,
            WalletPersistenceSupport walletPersistenceSupport,
            AddressDerivationService addressDerivationService) {
        this.walletLookupPort = walletLookupPort;
        this.walletAddressIndexPort = walletAddressIndexPort;
        this.walletPersistenceSupport = walletPersistenceSupport;
        this.addressDerivationService = addressDerivationService;
    }

    @Override
    public WalletEntity requireWallet(Long userId, String walletName) {
        WalletEntity wallet = walletLookupPort.findByNameAndUserId(walletName, userId);
        if (wallet == null) {
            throw new WalletExceptions.WalletNoExists("wallet not found");
        }
        return wallet;
    }

    @Override
    public WalletEntity save(WalletEntity wallet) {
        return walletPersistenceSupport.persist(wallet);
    }

    @Override
    public int incrementLastDerivedIndex(Long walletId) {
        return walletAddressIndexPort.incrementLastDerivedIndex(walletId);
    }

    @Override
    public String deriveAddressFromXpub(String xpub, int index) {
        return addressDerivationService.deriveAddressFromXpub(xpub, index);
    }

    @Override
    public String deriveAddress(Long walletId, String passphraseHash) {
        return addressDerivationService.deriveAddress(walletId, passphraseHash);
    }
}
