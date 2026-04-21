package source.treasury.infra.persistence;

import org.springframework.stereotype.Component;
import source.common.service.AddressDerivationService;
import source.treasury.application.port.out.TreasuryXpubValidationPort;

@Component
public class AddressDerivationTreasuryXpubValidationAdapter implements TreasuryXpubValidationPort {

    private final AddressDerivationService addressDerivationService;

    public AddressDerivationTreasuryXpubValidationAdapter(AddressDerivationService addressDerivationService) {
        this.addressDerivationService = addressDerivationService;
    }

    @Override
    public void validate(String auditXpub) {
        addressDerivationService.deriveAddressFromXpub(auditXpub, 0);
    }
}
