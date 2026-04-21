package source.transactions.infra.externalpayments;

import org.springframework.stereotype.Component;
import source.transactions.application.externalpayments.ExternalPaymentsCustodyPort;
import source.transactions.infra.CustodyGateway;

@Component
public class ExternalPaymentsCustodyAdapter implements ExternalPaymentsCustodyPort {

    private final CustodyGateway custodyGateway;

    public ExternalPaymentsCustodyAdapter(CustodyGateway custodyGateway) {
        this.custodyGateway = custodyGateway;
    }

    @Override
    public String providerName() {
        return custodyGateway.providerName();
    }

    @Override
    public PaymentResult sendOnchain(OnchainPaymentCommand command) {
        CustodyGateway.PaymentResult result = custodyGateway.sendOnchain(new CustodyGateway.OnchainPaymentCommand(
                command.userId(),
                command.walletId(),
                command.walletName(),
                command.destinationAddress(),
                command.amountSats(),
                command.description(),
                command.authorizationProof()));
        return new PaymentResult(
                result.providerReference(),
                result.txid(),
                result.paymentHash(),
                result.status(),
                result.feeSats(),
                result.rawPayload());
    }
}
