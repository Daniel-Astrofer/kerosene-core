package source.transactions.infra;

import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import source.transactions.application.externalpayments.ExternalPaymentsCustodyPort;
import source.transactions.service.BitcoinNodeService;

@Configuration
public class ExternalRailProviderConfiguration {

    static final String INVOICE_PROVIDER_PROPERTY = "transactions.rails.lightning.invoice-provider";
    static final String PAYMENT_PROVIDER_PROPERTY = "transactions.rails.lightning.payment-provider";

    @Bean("externalLightningInvoiceGateway")
    @ConditionalOnMissingBean(name = "externalLightningInvoiceGateway")
    public LightningInvoiceGateway externalLightningInvoiceGateway(
            Environment environment,
            ObjectProvider<BitcoinNodeService> lndGateway,
            ObjectProvider<BtcPayServerCustodyGateway> btcpayGateway,
            ObjectProvider<ConfigurableCustodyGateway> configurableGateway) {
        return chooseProvider(
                environment.getProperty(INVOICE_PROVIDER_PROPERTY, "auto"),
                "Lightning invoice",
                List.of(
                        candidate("lnd", lndGateway.getIfAvailable()),
                        candidate("btcpay", btcpayGateway.getIfAvailable()),
                        candidate("configurable", configurableGateway.getIfAvailable())));
    }

    @Bean("externalLightningPaymentGateway")
    @ConditionalOnMissingBean(name = "externalLightningPaymentGateway")
    public LightningPaymentGateway externalLightningPaymentGateway(
            Environment environment,
            ObjectProvider<BitcoinNodeService> lndGateway,
            ObjectProvider<BtcPayServerCustodyGateway> btcpayGateway,
            ObjectProvider<ConfigurableCustodyGateway> configurableGateway) {
        return chooseProvider(
                environment.getProperty(PAYMENT_PROVIDER_PROPERTY, "auto"),
                "Lightning payment",
                List.of(
                        candidate("lnd", lndGateway.getIfAvailable()),
                        candidate("btcpay", btcpayGateway.getIfAvailable()),
                        candidate("configurable", configurableGateway.getIfAvailable())));
    }

    @Bean
    public ExternalRailProviderRegistry externalRailProviderRegistry(
            @Qualifier("externalLightningInvoiceGateway") LightningInvoiceGateway lightningInvoiceGateway,
            @Qualifier("externalLightningPaymentGateway") LightningPaymentGateway lightningPaymentGateway,
            @Qualifier("bitcoinCorePsbtExternalPaymentsCustodyPort") ExternalPaymentsCustodyPort onchainCustodyPort) {
        return new ExternalRailProviderRegistry(
                lightningInvoiceGateway,
                lightningPaymentGateway,
                onchainCustodyPort);
    }

    static <T> T chooseProvider(
            String requestedProvider,
            String railName,
            List<RailProviderCandidate<T>> candidates) {
        String providerKey = requestedProvider == null || requestedProvider.isBlank()
                ? "auto"
                : requestedProvider.trim().toLowerCase(Locale.ROOT);

        if ("auto".equals(providerKey)) {
            return candidates.stream()
                    .filter(candidate -> candidate.provider() != null)
                    .findFirst()
                    .map(RailProviderCandidate::provider)
                    .orElseThrow(() -> new IllegalStateException(
                            "No provider bean is available for the " + railName + " rail."));
        }

        for (RailProviderCandidate<T> candidate : candidates) {
            if (!candidate.key().equals(providerKey)) {
                continue;
            }
            if (candidate.provider() == null) {
                throw new IllegalStateException(
                        "Configured provider " + providerKey + " is not available for the " + railName + " rail.");
            }
            return candidate.provider();
        }

        String allowedProviders = candidates.stream()
                .map(RailProviderCandidate::key)
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
        throw new IllegalStateException(
                "Unsupported provider " + providerKey + " for the " + railName
                        + " rail. Allowed providers: auto, " + allowedProviders + ".");
    }

    private static <T> RailProviderCandidate<T> candidate(String key, T provider) {
        return new RailProviderCandidate<>(key, provider);
    }

    record RailProviderCandidate<T>(String key, T provider) {
    }
}
