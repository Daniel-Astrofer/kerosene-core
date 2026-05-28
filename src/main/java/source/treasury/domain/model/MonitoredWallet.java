package source.treasury.domain.model;

public record MonitoredWallet(
        Long id,
        String xpub,
        Integer lastDerivedIndex,
        String depositAddress) {
}
