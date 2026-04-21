package source.treasury.application.revenue.handler;

import org.springframework.stereotype.Component;
import source.treasury.application.port.out.AuditAddressPort;
import source.treasury.application.revenue.AbstractRevenueCollectionHandler;
import source.treasury.application.revenue.RevenueCollectionContext;

@Component
public class AssignAuditAddressHandler extends AbstractRevenueCollectionHandler {

    private final AuditAddressPort auditAddressPort;

    public AssignAuditAddressHandler(AuditAddressPort auditAddressPort) {
        this.auditAddressPort = auditAddressPort;
    }

    @Override
    protected void doHandle(RevenueCollectionContext context) {
        context.setAuditAddress(auditAddressPort.getNextAuditAddress());
    }
}
