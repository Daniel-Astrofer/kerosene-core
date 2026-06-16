package source.treasury.application.port.out;

public interface MerkleLedgerPort {

    String appendEntry(String entryData);

    String getCurrentRoot();
}
