package source.transactions.infra.externalpayments;

import org.springframework.stereotype.Component;
import source.transactions.application.externalpayments.ExternalPaymentsCustodyPort;
import source.transactions.service.QuorumPsbtSigningService;

@Component
public class ExternalPaymentsCustodyAdapter implements ExternalPaymentsCustodyPort {

    private final QuorumPsbtSigningService quorumPsbtSigningService;

    public ExternalPaymentsCustodyAdapter(QuorumPsbtSigningService quorumPsbtSigningService) {
        this.quorumPsbtSigningService = quorumPsbtSigningService;
    }

    @Override
    public String providerName() {
        return "BITCOIN_CORE_QUORUM";
    }

    @Override
    public PaymentResult sendOnchain(OnchainPaymentCommand command) {
        QuorumPsbtSigningService.OnchainExecution result = quorumPsbtSigningService.execute(command);
        return new PaymentResult(
                result.txid(),
                result.txid(),
                null,
                "MEMPOOL",
                result.feeSats(),
                result.combinedPsbt());
    }
}
