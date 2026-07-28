package com.kerosene.common.vaultmesh;

/**
 * Controls whether PSBT signing also durably consumes the intent.
 */
public enum IntentCommitMode {
    /** Only produce partial signatures; intent stays reserved (CHANNELS→LND inject). */
    SIGN_ONLY,
    /** Produce partial signatures AND durably consume the intent (USERS / standalone). */
    SIGN_AND_COMMIT
}
