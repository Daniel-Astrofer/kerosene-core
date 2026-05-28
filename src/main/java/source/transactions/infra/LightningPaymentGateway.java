package source.transactions.infra;

public interface LightningPaymentGateway {

    boolean isLive();

    String providerName();

    CustodyGateway.PaymentResult payLightning(CustodyGateway.LightningPaymentCommand command);
}
