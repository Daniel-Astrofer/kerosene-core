package source.treasury.infra.reserve;

import org.springframework.stereotype.Component;
import source.transactions.infra.LightningClient;
import source.treasury.application.port.out.LightningReservePort;

@Component
public class LightningReserveAdapter implements LightningReservePort {

    private final LightningClient lightningClient;

    public LightningReserveAdapter(LightningClient lightningClient) {
        this.lightningClient = lightningClient;
    }

    @Override
    public long getLightningNodeBalance() {
        return lightningClient.getLightningNodeBalance();
    }
}
