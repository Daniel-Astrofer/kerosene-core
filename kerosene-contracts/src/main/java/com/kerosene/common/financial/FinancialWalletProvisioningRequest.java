package com.kerosene.common.financial;

public record FinancialWalletProvisioningRequest(Long userId, String initialAddress) {

    public FinancialWalletProvisioningRequest {
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (initialAddress == null || initialAddress.isBlank()) throw new IllegalArgumentException("initialAddress required");
    }
}
