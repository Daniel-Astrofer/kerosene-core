package com.kerosene.sovereign.integration;

import org.springframework.stereotype.Component;
import com.kerosene.common.financial.FinancialMpcKeyPort;

import java.util.UUID;

/**
 * Fail-closed custodial keygen port. Platform treasury key material is mesh
 * genesis/DKG only; the legacy mpc-sidecar management path is removed.
 */
@Component
public class SovereignFinancialMpcKeyAdapter implements FinancialMpcKeyPort {

    @Override
    public String keygenWallet(UUID walletId, Long userId) {
        throw new IllegalStateException(
                "Custodial keygen via mpc-sidecar is removed. "
                        + "Wallet keys are vault-mesh genesis/DKG only "
                        + "(walletId=" + walletId + ", userId=" + userId + ").");
    }
}
