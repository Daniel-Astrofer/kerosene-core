package source.sovereign.integration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import source.common.financial.FinancialMpcKeyPort;
import source.sovereign.mpc.MpcSidecarClient;

import java.util.UUID;

@Component
public class SovereignFinancialMpcKeyAdapter implements FinancialMpcKeyPort {

    private final ObjectProvider<MpcSidecarClient> mpcClientProvider;
    private final int threshold;
    private final int totalParties;

    public SovereignFinancialMpcKeyAdapter(
            ObjectProvider<MpcSidecarClient> mpcClientProvider,
            @Value("${kfe.mpc.threshold:2}") int threshold,
            @Value("${kfe.mpc.total-parties:3}") int totalParties) {
        this.mpcClientProvider = mpcClientProvider;
        this.threshold = Math.max(1, threshold);
        this.totalParties = Math.max(this.threshold, totalParties);
    }

    @Override
    public String keygenWallet(UUID walletId, Long userId) {
        MpcSidecarClient client = mpcClientProvider.getIfAvailable();
        if (client == null || !client.isInitialized()) {
            throw new IllegalStateException("MPC sidecar is required for custodial wallet keygen.");
        }
        return client.keygen("kfe-wallet-" + walletId + "-user-" + userId, threshold, totalParties);
    }
}
