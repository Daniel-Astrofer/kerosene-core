package source.wallet.application.port.out;

import source.wallet.model.WalletEntity;

public interface WalletAddressProvisionPort {

    Allocation allocate(Long userId, WalletEntity wallet, String label, boolean forceFresh);

    record Allocation(
            String depositAddress,
            String externalReference,
            String source,
            boolean reusedExisting) {
    }
}
