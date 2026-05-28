package source.wallet.infra;

import org.springframework.stereotype.Component;
import source.transactions.service.CustodialAddressAllocator;
import source.wallet.application.port.out.WalletAddressProvisionPort;
import source.wallet.model.WalletEntity;

@Component
public class WalletAddressProvisionAdapter implements WalletAddressProvisionPort {

    private final CustodialAddressAllocator custodialAddressAllocator;

    public WalletAddressProvisionAdapter(CustodialAddressAllocator custodialAddressAllocator) {
        this.custodialAddressAllocator = custodialAddressAllocator;
    }

    @Override
    public Allocation allocate(Long userId, WalletEntity wallet, String label, boolean forceFresh) {
        CustodialAddressAllocator.Allocation allocation =
                custodialAddressAllocator.allocate(userId, wallet, label, forceFresh);
        return new Allocation(
                allocation.address(),
                allocation.externalReference(),
                allocation.provider(),
                allocation.reused());
    }
}
