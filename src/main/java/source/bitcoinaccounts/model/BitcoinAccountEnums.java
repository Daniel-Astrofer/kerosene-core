package source.bitcoinaccounts.model;

public final class BitcoinAccountEnums {

    private BitcoinAccountEnums() {
    }

    public enum AccountType {
        INTERNAL_CARD,
        WATCH_ONLY_COLD_WALLET,
        LIGHTNING_INTERNAL
    }

    public enum CustodyType {
        KEROSENE_CUSTODIAL,
        USER_SELF_CUSTODY,
        WATCH_ONLY
    }

    public enum AccountStatus {
        ACTIVE,
        FROZEN,
        EXPIRED,
        REPLACED,
        SAFETY_LOCKED,
        USER_ACTION_REQUIRED
    }

    public enum CardStatus {
        ACTIVE,
        FROZEN,
        EXPIRED,
        REPLACED,
        SAFETY_LOCKED
    }

    public enum ReceivingRequestStatus {
        ACTIVE,
        EXPIRED,
        MEMPOOL_SEEN,
        CONFIRMING,
        PAID,
        EXPIRED_RECEIVED,
        AUTO_RESOLUTION_PENDING,
        USER_ACTION_REQUIRED,
        HIDDEN,
        FAILED_SAFE
    }

    public enum ReceivingAddressStatus {
        UNUSED,
        ASSIGNED,
        OBSERVED,
        EXPIRED,
        EXPIRED_RECEIVED,
        USER_ACTION_REQUIRED,
        SAFETY_LOCKED
    }

    public enum ScriptType {
        P2WPKH,
        P2TR
    }

    public enum LedgerDirection {
        CREDIT,
        DEBIT
    }

    public enum LedgerEntryStatus {
        PENDING,
        AVAILABLE,
        LOCKED,
        AUTO_HOLD,
        FINALIZED,
        REVERSED,
        FAILED_SAFE
    }

    public enum ScriptPolicy {
        SINGLE_SIG,
        MULTISIG
    }

    public enum UtxoStatus {
        UNSPENT,
        SPENT,
        LOCKED
    }

    public enum PsbtStatus {
        DRAFT,
        UNSIGNED_CREATED,
        WAITING_EXTERNAL_SIGNATURE,
        SIGNED_SUBMITTED,
        VALIDATED,
        REJECTED_TAMPERED,
        REJECTED_POLICY,
        BROADCASTED,
        CONFIRMED,
        FAILED_SAFE
    }

    public enum TaxEventType {
        DEPOSIT_INTERNAL,
        WITHDRAWAL_SELF_CUSTODY,
        PAYMENT_SPEND,
        INTERNAL_TRANSFER,
        LIGHTNING_OPEN,
        LIGHTNING_CLOSE,
        FEE_PAID,
        COLD_WALLET_OBSERVED_IN,
        COLD_WALLET_OBSERVED_OUT
    }
}
