package source.treasury.application.port.out;

import java.util.List;

public interface AuditAddressPort {

    String getNextAuditAddress();

    void replenishWhitelist(List<String> newAddresses);
}
