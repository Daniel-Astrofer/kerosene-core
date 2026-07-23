package source.sovereign.integration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import source.common.financial.FinancialMpcKeyPort;
import source.sovereign.mpc.MpcSidecarClient;

import java.util.UUID;

/**
 * Legacy mpc-sidecar keygen adapter. Under mesh-only cutover this fails closed —
 * treasury wallet material is genesis/DKG on the vault mesh, never sidecar keygen.
 */
@Component
public class SovereignFinancialMpcKeyAdapter implements FinancialMpcKeyPort {

    private final ObjectProvider<MpcSidecarClient> mpcClientProvider;
    private final boolean meshOnly;
    private final boolean mpcSigningEnabled;
    private final int threshold;
    private final int totalParties;

    public SovereignFinancialMpcKeyAdapter(
            ObjectProvider<MpcSidecarClient> mpcClientProvider,
            @Value("${kfe.vaultmesh.mesh-only:false}") boolean meshOnly,
            @Value("${kfe.mpc.signing-enabled:true}") boolean mpcSigningEnabled,
            @Value("${kfe.mpc.threshold:2}") int threshold,
            @Value("${kfe.mpc.total-parties:3}") int totalParties) {
        this.mpcClientProvider = mpcClientProvider;
        this.meshOnly = meshOnly;
        this.mpcSigningEnabled = mpcSigningEnabled;
        this.threshold = Math.max(1, threshold);
        this.totalParties = Math.max(this.threshold, totalParties);
    }

    @Override
    public String keygenWallet(UUID walletId, Long userId) {
        if (meshOnly || !mpcSigningEnabled) {
            throw new IllegalStateException(
                    "Custodial keygen via mpc-sidecar is disabled under vault mesh "
                            + "(kfe.vaultmesh.mesh-only / kfe.mpc.signing-enabled=false). "
                            + "Wallet keys are mesh genesis/DKG only.");
        }
        MpcSidecarClient client = mpcClientProvider.getIfAvailable();
        if (client == null || !client.isInitialized()) {
            throw new IllegalStateException("MPC sidecar is required for custodial wallet keygen.");
        }
        return client.keygen("kfe-wallet-" + walletId + "-user-" + userId, threshold, totalParties);
    }
}
