package source.transactions.infra;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExternalRailProviderConfigurationTest {

    @Test
    void autoSelectsFirstAvailableProvider() {
        LightningInvoiceGateway lnd = mock(LightningInvoiceGateway.class);
        LightningInvoiceGateway configurable = mock(LightningInvoiceGateway.class);

        LightningInvoiceGateway selected = ExternalRailProviderConfiguration.chooseProvider(
                "auto",
                "Lightning invoice",
                List.of(
                        new ExternalRailProviderConfiguration.RailProviderCandidate<>("lnd", lnd),
                        new ExternalRailProviderConfiguration.RailProviderCandidate<>("configurable", configurable)));

        assertSame(lnd, selected);
    }

    @Test
    void explicitSelectionSkipsEarlierAvailableProvider() {
        LightningPaymentGateway lnd = mock(LightningPaymentGateway.class);
        LightningPaymentGateway btcpay = mock(LightningPaymentGateway.class);

        LightningPaymentGateway selected = ExternalRailProviderConfiguration.chooseProvider(
                "btcpay",
                "Lightning payment",
                List.of(
                        new ExternalRailProviderConfiguration.RailProviderCandidate<>("lnd", lnd),
                        new ExternalRailProviderConfiguration.RailProviderCandidate<>("btcpay", btcpay)));

        assertSame(btcpay, selected);
    }

    @Test
    void explicitUnavailableProviderFailsClosed() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ExternalRailProviderConfiguration.chooseProvider(
                        "lnd",
                        "Lightning invoice",
                        List.of(new ExternalRailProviderConfiguration.RailProviderCandidate<LightningInvoiceGateway>(
                                "lnd",
                                null))));

        assertTrue(exception.getMessage().contains(
                "Configured provider lnd is not available for the Lightning invoice rail"));
    }
}
