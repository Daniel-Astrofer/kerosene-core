package source.treasury.application.port.out;

import source.treasury.domain.model.TreasuryConfigState;

import java.util.Optional;

public interface TreasuryConfigPort {

    Optional<TreasuryConfigState> loadGlobalConfig();

    TreasuryConfigState loadOrCreateGlobalConfig();

    TreasuryConfigState saveGlobalConfig(TreasuryConfigState configState);
}
