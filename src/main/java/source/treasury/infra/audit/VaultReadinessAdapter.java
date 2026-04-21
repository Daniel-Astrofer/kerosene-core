package source.treasury.infra.audit;

import org.springframework.stereotype.Component;
import source.security.VaultKeyProvider;
import source.treasury.application.port.out.VaultReadinessPort;

@Component
public class VaultReadinessAdapter implements VaultReadinessPort {

    private final VaultKeyProvider vaultKeyProvider;

    public VaultReadinessAdapter(VaultKeyProvider vaultKeyProvider) {
        this.vaultKeyProvider = vaultKeyProvider;
    }

    @Override
    public boolean isReady() {
        return vaultKeyProvider.isReady();
    }
}
