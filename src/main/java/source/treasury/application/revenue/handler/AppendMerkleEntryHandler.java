package source.treasury.application.revenue.handler;

import org.springframework.stereotype.Component;
import source.treasury.application.port.out.MerkleLedgerPort;
import source.treasury.application.revenue.AbstractRevenueCollectionHandler;
import source.treasury.application.revenue.RevenueCollectionContext;

@Component
public class AppendMerkleEntryHandler extends AbstractRevenueCollectionHandler {

    private final MerkleLedgerPort merkleLedgerPort;

    public AppendMerkleEntryHandler(MerkleLedgerPort merkleLedgerPort) {
        this.merkleLedgerPort = merkleLedgerPort;
    }

    @Override
    protected void doHandle(RevenueCollectionContext context) {
        String entry = "PROFIT_COLLECTED:%d:%s".formatted(
                context.profitSats(),
                context.profitBtc().toPlainString());
        context.setMerkleRoot(merkleLedgerPort.appendEntry(entry));
    }
}
