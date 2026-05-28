package source.transactions.service;

import org.junit.jupiter.api.Test;
import source.transactions.infra.CustodyGateway;
import source.transactions.infra.LightningInvoiceGateway;
import source.transactions.infra.LightningPaymentGateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitcoinNodeServiceRailContractTest {

    @Test
    void lndServiceExposesOnlyLightningGatewayContracts() {
        assertTrue(LightningInvoiceGateway.class.isAssignableFrom(BitcoinNodeService.class));
        assertTrue(LightningPaymentGateway.class.isAssignableFrom(BitcoinNodeService.class));
        assertFalse(CustodyGateway.class.isAssignableFrom(BitcoinNodeService.class));
    }
}
