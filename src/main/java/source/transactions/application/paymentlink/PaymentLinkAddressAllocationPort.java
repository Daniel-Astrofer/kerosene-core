package source.transactions.application.paymentlink;

import source.wallet.model.WalletEntity;

public interface PaymentLinkAddressAllocationPort {

    Allocation allocate(Long userId, WalletEntity wallet, String label, boolean forceFresh);

    record Allocation(
            String address,
            String externalReference,
            String provider,
            boolean reused) {
    }
}
