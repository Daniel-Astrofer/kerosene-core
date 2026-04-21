package source.treasury.infra.persistence;

import org.springframework.stereotype.Component;
import source.treasury.application.port.out.TreasuryConfigPort;
import source.treasury.domain.model.TreasuryConfigState;
import source.treasury.entity.TreasuryConfig;
import source.treasury.repository.TreasuryConfigRepository;

import java.util.Optional;

@Component
public class TreasuryConfigPersistenceAdapter implements TreasuryConfigPort {

    private final TreasuryConfigRepository treasuryConfigRepository;

    public TreasuryConfigPersistenceAdapter(TreasuryConfigRepository treasuryConfigRepository) {
        this.treasuryConfigRepository = treasuryConfigRepository;
    }

    @Override
    public Optional<TreasuryConfigState> loadGlobalConfig() {
        return treasuryConfigRepository.getGlobalConfig().map(this::toState);
    }

    @Override
    public TreasuryConfigState loadOrCreateGlobalConfig() {
        return treasuryConfigRepository.getGlobalConfig()
                .map(this::toState)
                .orElseGet(() -> toState(treasuryConfigRepository.save(newGlobalConfig())));
    }

    @Override
    public TreasuryConfigState saveGlobalConfig(TreasuryConfigState configState) {
        TreasuryConfig entity = treasuryConfigRepository.getGlobalConfig().orElseGet(this::newGlobalConfig);
        entity.setMaxWithdrawLimit(configState.maxWithdrawLimit());
        entity.setAuditXpub(configState.auditXpub());
        return toState(treasuryConfigRepository.save(entity));
    }

    private TreasuryConfig newGlobalConfig() {
        TreasuryConfig config = new TreasuryConfig();
        config.setId(1L);
        return config;
    }

    private TreasuryConfigState toState(TreasuryConfig config) {
        return new TreasuryConfigState(
                config.getMaxWithdrawLimit(),
                config.getAuditXpub(),
                config.getUpdatedAt());
    }
}
