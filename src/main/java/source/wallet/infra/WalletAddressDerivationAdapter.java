package source.wallet.infra;

import org.springframework.stereotype.Component;
import source.common.service.AddressDerivationService;
import source.wallet.application.port.out.WalletAddressDerivationPort;

@Component
public class WalletAddressDerivationAdapter implements WalletAddressDerivationPort {

    private final AddressDerivationService addressDerivationService;

    public WalletAddressDerivationAdapter(AddressDerivationService addressDerivationService) {
        this.addressDerivationService = addressDerivationService;
    }

    @Override
    public String deriveAddressFromXpub(String xpub, int index) {
        return addressDerivationService.deriveAddressFromXpub(xpub, index);
    }
}
