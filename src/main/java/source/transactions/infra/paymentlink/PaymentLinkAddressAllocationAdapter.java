package source.transactions.infra.paymentlink;

import org.springframework.stereotype.Component;
import source.transactions.application.paymentlink.PaymentLinkAddressAllocationPort;
import source.transactions.service.CustodialAddressAllocator;
import source.wallet.model.WalletEntity;

@Component
public class PaymentLinkAddressAllocationAdapter implements PaymentLinkAddressAllocationPort {

    private final CustodialAddressAllocator custodialAddressAllocator;

    public PaymentLinkAddressAllocationAdapter(CustodialAddressAllocator custodialAddressAllocator) {
        this.custodialAddressAllocator = custodialAddressAllocator;
    }

    @Override
    public Allocation allocate(Long userId, WalletEntity wallet, String label, boolean forceFresh) {
        CustodialAddressAllocator.Allocation allocation = custodialAddressAllocator.allocate(
                userId,
                wallet,
                label,
                forceFresh);
        return new Allocation(
                allocation.address(),
                allocation.externalReference(),
                allocation.provider(),
                allocation.reused());
    }
}
