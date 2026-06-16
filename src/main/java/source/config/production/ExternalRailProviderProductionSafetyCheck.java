package source.config.production;

import org.springframework.util.ClassUtils;
import source.kfe.rail.ConfigurableCustodyGateway;
import source.kfe.rail.KfeOnchainPaymentGateway;
import source.kfe.rail.LightningInvoiceGateway;
import source.kfe.rail.LightningPaymentGateway;

public class ExternalRailProviderProductionSafetyCheck extends AbstractProductionSafetyCheck {

    private static final String LIGHTNING_INVOICE_BEAN = "externalLightningInvoiceGateway";
    private static final String LIGHTNING_PAYMENT_BEAN = "externalLightningPaymentGateway";
    private static final String ONCHAIN_BEAN = "bitcoinCorePsbtKfeOnchainPaymentGateway";
    private static final String ONCHAIN_PROVIDER_NAME = "BITCOIN_CORE_QUORUM";

    public ExternalRailProviderProductionSafetyCheck(ProductionSafetyCheck next) {
        super(next);
    }

    @Override
    protected void inspect(ProductionSafetyContext context) {
        LightningInvoiceGateway invoiceGateway = requireBean(
                context,
                LIGHTNING_INVOICE_BEAN,
                LightningInvoiceGateway.class,
                "Lightning invoice rail");
        if (invoiceGateway != null) {
            inspectLightningGateway(context, "Lightning invoice rail", invoiceGateway);
        }

        LightningPaymentGateway paymentGateway = requireBean(
                context,
                LIGHTNING_PAYMENT_BEAN,
                LightningPaymentGateway.class,
                "Lightning payment rail");
        if (paymentGateway != null) {
            inspectLightningGateway(context, "Lightning payment rail", paymentGateway);
        }

        KfeOnchainPaymentGateway onchainPort = requireBean(
                context,
                ONCHAIN_BEAN,
                KfeOnchainPaymentGateway.class,
                "On-chain outbound rail");
        if (onchainPort != null && !ONCHAIN_PROVIDER_NAME.equalsIgnoreCase(safeProviderName(onchainPort))) {
            context.addViolation("On-chain outbound rail must use " + ONCHAIN_PROVIDER_NAME + " in prod");
        }
    }

    private <T> T requireBean(
            ProductionSafetyContext context,
            String beanName,
            Class<T> beanType,
            String railName) {
        try {
            if (!context.beanFactory().containsBean(beanName)) {
                context.addViolation(railName + " provider bean " + beanName + " must be available in prod");
                return null;
            }
            return context.beanFactory().getBean(beanName, beanType);
        } catch (RuntimeException exception) {
            context.addViolation(railName + " provider bean " + beanName + " could not be verified");
            return null;
        }
    }

    private void inspectLightningGateway(
            ProductionSafetyContext context,
            String railName,
            Object gateway) {
        if (isWeakConfigurableGateway(gateway)) {
            context.addViolation(railName + " must not use configurable custody gateway in prod");
        }
        if (!safeLive(gateway)) {
            context.addViolation(railName + " provider must be live in prod");
        }
    }

    private boolean isWeakConfigurableGateway(Object gateway) {
        Class<?> userClass = ClassUtils.getUserClass(gateway);
        return ConfigurableCustodyGateway.class.isAssignableFrom(userClass)
                || "ConfigurableCustodyGateway".equals(userClass.getSimpleName())
                || "BCX".equalsIgnoreCase(safeProviderName(gateway));
    }

    private boolean safeLive(Object gateway) {
        try {
            if (gateway instanceof LightningInvoiceGateway invoiceGateway) {
                return invoiceGateway.isLive();
            }
            if (gateway instanceof LightningPaymentGateway paymentGateway) {
                return paymentGateway.isLive();
            }
            return false;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String safeProviderName(Object gateway) {
        try {
            if (gateway instanceof LightningInvoiceGateway invoiceGateway) {
                return invoiceGateway.providerName();
            }
            if (gateway instanceof LightningPaymentGateway paymentGateway) {
                return paymentGateway.providerName();
            }
            if (gateway instanceof KfeOnchainPaymentGateway custodyPort) {
                return custodyPort.providerName();
            }
            return "";
        } catch (RuntimeException exception) {
            return "";
        }
    }
}
