package source.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import source.auth.application.service.identityaccess.PlatformTransactionSignerPort;
import source.auth.model.entity.UserDataBase;
import source.config.production.ProductionProfileDetector;
import source.config.production.ProductionSafetyCheckChain;
import source.transactions.application.externalpayments.ExternalPaymentsCustodyPort;
import source.transactions.infra.CustodyGateway;
import source.transactions.infra.LightningInvoiceGateway;
import source.transactions.infra.LightningPaymentGateway;

class ProductionMockProfileConditionTest {

    @Test
    void shouldFailFastWhenUnsafeProductionSettingsAreDetected() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("bitcoin.mock-mode", "true");
        environment.setProperty("treasury.siphon.manual-settlement-enabled", "true");
        environment.setProperty("vault.enabled", "false");
        environment.setProperty("mpc.sidecar.tls.enabled", "false");
        environment.setProperty("app.cors.allowed-origins", "*");

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("forbiddenMockService", new ForbiddenMockService());

        ProductionMockProfileCondition condition = new ProductionMockProfileCondition(
                new ProductionProfileDetector(environment),
                new ProductionSafetyCheckChain(environment, beanFactory));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> condition.run(new DefaultApplicationArguments(new String[0])));

        assertTrue(exception.getMessage().contains(
                "bean " + ForbiddenMockService.class.getName() + " is not allowed in prod"));
        assertTrue(exception.getMessage().contains("bitcoin.mock-mode=true"));
        assertTrue(exception.getMessage().contains("treasury.siphon.manual-settlement-enabled=true"));
        assertTrue(exception.getMessage().contains("vault.enabled must be true"));
        assertTrue(exception.getMessage().contains("vault.raft.required must be true"));
        assertTrue(exception.getMessage().contains("mpc.sidecar.tls.enabled must be true"));
        assertTrue(exception.getMessage().contains("bitcoin.rpc.required must be true"));
        assertTrue(exception.getMessage().contains("release.attestation.required must be true"));
        assertTrue(exception.getMessage().contains("wildcard CORS is not allowed"));
        assertTrue(exception.getMessage().contains("quorum.shard.urls must define remote shard peers"));
        assertTrue(exception.getMessage().contains("lightning.lnd.host must be configured"));
        assertTrue(exception.getMessage().contains("lightning.lnd.tls.cert-path must be configured"));
        assertTrue(exception.getMessage().contains("bitcoin.platform.master-xpub must be configured"));
        assertTrue(exception.getMessage().contains("quorum.psbt.signer-urls must be configured"));
        assertTrue(exception.getMessage().contains("quorum.psbt.signer-ids must be configured"));
        assertTrue(exception.getMessage().contains("platform MPC signer must be available in prod"));
        assertTrue(exception.getMessage().contains(
                "Lightning invoice rail provider bean externalLightningInvoiceGateway must be available in prod"));
        assertTrue(exception.getMessage().contains(
                "Lightning payment rail provider bean externalLightningPaymentGateway must be available in prod"));
        assertTrue(exception.getMessage().contains(
                "On-chain outbound rail provider bean bitcoinCorePsbtExternalPaymentsCustodyPort must be available in prod"));
    }

    @Test
    void shouldSkipValidationOutsideProductionProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        environment.setProperty("bitcoin.mock-mode", "true");

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("forbiddenMockService", new ForbiddenMockService());

        ProductionMockProfileCondition condition = new ProductionMockProfileCondition(
                new ProductionProfileDetector(environment),
                new ProductionSafetyCheckChain(environment, beanFactory));

        assertDoesNotThrow(() -> condition.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldAllowProductionWhenRequiredControlsAreConfigured() {
        MockEnvironment environment = safeProductionEnvironment();

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("platformSigner", new AvailablePlatformSigner());
        beanFactory.addBean("externalLightningInvoiceGateway", new LiveLightningGateway());
        beanFactory.addBean("externalLightningPaymentGateway", new LiveLightningGateway());
        beanFactory.addBean("bitcoinCorePsbtExternalPaymentsCustodyPort", new AvailableOnchainCustodyPort());

        ProductionMockProfileCondition condition = new ProductionMockProfileCondition(
                new ProductionProfileDetector(environment),
                new ProductionSafetyCheckChain(environment, beanFactory));

        assertDoesNotThrow(() -> condition.run(new DefaultApplicationArguments(new String[0])));
    }

    @Test
    void shouldRejectWeakConfigurableRailProviderInProduction() {
        MockEnvironment environment = safeProductionEnvironment();

        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("platformSigner", new AvailablePlatformSigner());
        beanFactory.addBean("externalLightningInvoiceGateway", new WeakConfigurableLightningGateway());
        beanFactory.addBean("externalLightningPaymentGateway", new WeakConfigurableLightningGateway());
        beanFactory.addBean("bitcoinCorePsbtExternalPaymentsCustodyPort", new AvailableOnchainCustodyPort());

        ProductionMockProfileCondition condition = new ProductionMockProfileCondition(
                new ProductionProfileDetector(environment),
                new ProductionSafetyCheckChain(environment, beanFactory));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> condition.run(new DefaultApplicationArguments(new String[0])));

        assertTrue(exception.getMessage().contains(
                "Lightning invoice rail must not use configurable custody gateway in prod"));
        assertTrue(exception.getMessage().contains(
                "Lightning payment rail must not use configurable custody gateway in prod"));
    }

    private MockEnvironment safeProductionEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("vault.enabled", "true");
        environment.setProperty("vault.raft.enabled", "true");
        environment.setProperty("vault.raft.required", "true");
        environment.setProperty("mpc.sidecar.tls.enabled", "true");
        environment.setProperty("lightning.lnd.enabled", "true");
        environment.setProperty("bitcoin.rpc.enabled", "true");
        environment.setProperty("bitcoin.rpc.required", "true");
        environment.setProperty("bitcoin.rpc.pruned-required", "true");
        environment.setProperty("tor.health.required", "true");
        environment.setProperty("release.attestation.required", "true");
        environment.setProperty("release.attestation.remote.enabled", "true");
        environment.setProperty("app.cors.allowed-origins", "https://app.kerosene.example");
        environment.setProperty("webauthn.relying-party-id", "app.kerosene.example");
        environment.setProperty("quorum.shard.urls", "https://shard-a.onion,https://shard-b.onion");
        environment.setProperty("quorum.psbt.signer-urls", "https://signer-a.onion/sign,https://signer-b.onion/sign");
        environment.setProperty("quorum.psbt.signer-ids", "signer-a,signer-b");
        environment.setProperty("quorum.psbt.require-signer-identity", "true");
        environment.setProperty("lightning.lnd.host", "lnd");
        environment.setProperty("lightning.lnd.tls.cert-path", "/run/secrets/lnd/tls.cert");
        environment.setProperty("lightning.lnd.macaroon-path", "/run/secrets/lnd/admin.macaroon");
        environment.setProperty("bitcoin.platform.master-xpub", "xpub-production-placeholder");
        environment.setProperty("shard.attestation.secret", "cluster-attestation-secret");
        environment.setProperty("mpc.sidecar.host", "mpc-sidecar");
        environment.setProperty("mpc.sidecar.tls.cert-chain", "/certs/client.crt");
        environment.setProperty("mpc.sidecar.tls.private-key", "/certs/client.key");
        environment.setProperty("mpc.sidecar.tls.trust-cert-collection", "/certs/rootCA.crt");
        environment.setProperty("btcpay.enabled", "true");
        return environment;
    }

    private static final class ForbiddenMockService {
    }

    private static final class AvailablePlatformSigner implements PlatformTransactionSignerPort {

        @Override
        public String sign(UserDataBase user) {
            return "signed";
        }
    }

    private static class LiveLightningGateway implements LightningInvoiceGateway, LightningPaymentGateway {

        @Override
        public boolean isLive() {
            return true;
        }

        @Override
        public String providerName() {
            return "LND_BITCOIND_PRUNED";
        }

        @Override
        public CustodyGateway.GeneratedLightningInvoice createLightningInvoice(
                CustodyGateway.LightningInvoiceCommand command) {
            return null;
        }

        @Override
        public CustodyGateway.IncomingLightningInvoiceStatus getLightningInvoiceStatus(
                CustodyGateway.LightningInvoiceStatusCommand command) {
            return null;
        }

        @Override
        public boolean cancelLightningInvoice(CustodyGateway.LightningInvoiceCancellationCommand command) {
            return false;
        }

        @Override
        public CustodyGateway.PaymentResult payLightning(CustodyGateway.LightningPaymentCommand command) {
            return null;
        }
    }

    private static final class WeakConfigurableLightningGateway extends LiveLightningGateway {

        @Override
        public String providerName() {
            return "BCX";
        }
    }

    private static final class AvailableOnchainCustodyPort implements ExternalPaymentsCustodyPort {

        @Override
        public String providerName() {
            return "BITCOIN_CORE_QUORUM";
        }

        @Override
        public PaymentResult sendOnchain(OnchainPaymentCommand command) {
            return null;
        }
    }
}
