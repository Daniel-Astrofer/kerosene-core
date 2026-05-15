package source.transactions.infra;

import org.springframework.stereotype.Component;

/**
 * BTCPay Server / LND Lightning Client implementation.
 */
@Component
public class BtcPayLightningClient implements LightningClient {

    @Override
    public long getLocalBalance() {
        // Placeholder until BTCPay API connection is established
        return 10_000_000L; // 0.1 BTC
    }

    @Override
    public long getRemoteBalance() {
        // Placeholder
        return 5_000_000L; // 0.05 BTC
    }

    @Override
    public long getLightningNodeBalance() {
        return getLocalBalance() + getRemoteBalance();
    }

    @Override
    public double getNodeUptime() {
        // Placeholder for node health check ( uptime stats )
        return 0.9999; // 99.99% uptime
    }

    @Override
    public long getLspLatency() {
        // Placeholder for LSP Node latency (ms)
        return 120; // 120ms
    }
}
