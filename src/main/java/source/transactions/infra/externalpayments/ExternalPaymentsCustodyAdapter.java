package source.transactions.infra.externalpayments;

import org.springframework.stereotype.Component;
import source.transactions.application.externalpayments.ExternalPaymentsCustodyPort;
import source.transactions.service.QuorumPsbtSigningService;

@Component("bitcoinCorePsbtExternalPaymentsCustodyPort")
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
    public OnchainFundingPreflight preflightOnchain(OnchainPreflightCommand command) {
        QuorumPsbtSigningService.OnchainFundingPreflight preflight = quorumPsbtSigningService.preflight(command);
        return new OnchainFundingPreflight(
                true,
                preflight.feeSats(),
                preflight.psbtHash(),
                preflight.configuredSignerCount(),
                providerName());
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
                result.metadataJson());
    }
}
