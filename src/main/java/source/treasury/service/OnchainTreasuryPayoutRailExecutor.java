package source.treasury.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import source.ledger.entity.SiphonRequest;
import source.transactions.application.externalpayments.ExternalPaymentsCustodyPort;
import source.transactions.application.externalpayments.ExternalPaymentsMath;

@Service
public class OnchainTreasuryPayoutRailExecutor implements TreasuryPayoutRailExecutor {

    private final ExternalPaymentsCustodyPort custodyPort;
    private final ExternalPaymentsMath externalPaymentsMath;

    public OnchainTreasuryPayoutRailExecutor(
            @Qualifier("bitcoinCorePsbtExternalPaymentsCustodyPort")
            ExternalPaymentsCustodyPort custodyPort,
            ExternalPaymentsMath externalPaymentsMath) {
        this.custodyPort = custodyPort;
        this.externalPaymentsMath = externalPaymentsMath;
    }

    @Override
    public ExecutionResult execute(SiphonRequest request) {
        ExternalPaymentsCustodyPort.PaymentResult result = custodyPort.sendOnchain(
                new ExternalPaymentsCustodyPort.OnchainPaymentCommand(
                        null,
                        null,
                        "TREASURY",
                        request.getDestinationAddress(),
                        externalPaymentsMath.btcToSats(request.getAmount()),
                        0L,
                        "TREASURY_PAYOUT:" + request.getId(),
                        request.getIdempotencyKey(),
                        request.getApprovalReference()));

        return new ExecutionResult(
                firstNonBlank(result.providerReference(), result.txid()),
                result.txid(),
                firstNonBlank(result.status(), "MEMPOOL"),
                result.feeSats(),
                result.rawPayload());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
