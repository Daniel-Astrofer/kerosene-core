package source.transactions.infra.externalpayments;

import org.springframework.stereotype.Component;
import source.common.service.AddressDerivationService;
import source.transactions.application.externalpayments.ExternalPaymentsWalletPort;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;
import source.wallet.service.WalletService;

@Component
public class ExternalPaymentsWalletAdapter implements ExternalPaymentsWalletPort {

    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final AddressDerivationService addressDerivationService;

    public ExternalPaymentsWalletAdapter(
            WalletService walletService,
            WalletRepository walletRepository,
            AddressDerivationService addressDerivationService) {
        this.walletService = walletService;
        this.walletRepository = walletRepository;
        this.addressDerivationService = addressDerivationService;
    }

    @Override
    public WalletEntity requireWallet(Long userId, String walletName) {
        WalletEntity wallet = walletService.findByNameAndUserId(walletName, userId);
        if (wallet == null) {
            throw new WalletExceptions.WalletNoExists("wallet not found");
        }
        return wallet;
    }

    @Override
    public WalletEntity save(WalletEntity wallet) {
        return walletRepository.save(wallet);
    }

    @Override
    public int incrementLastDerivedIndex(Long walletId) {
        return walletService.incrementLastDerivedIndex(walletId);
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
