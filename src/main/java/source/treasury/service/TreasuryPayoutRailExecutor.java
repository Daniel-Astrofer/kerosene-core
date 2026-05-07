package source.treasury.service;

import source.ledger.entity.SiphonRequest;

public interface TreasuryPayoutRailExecutor {

    ExecutionResult execute(SiphonRequest request);

    record ExecutionResult(
            String providerReference,
            String blockchainTxid,
            String providerStatus,
            long networkFeeSats,
            String rawPayload) {
    }
}
