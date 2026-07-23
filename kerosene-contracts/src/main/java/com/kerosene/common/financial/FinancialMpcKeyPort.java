package com.kerosene.common.financial;

import java.util.UUID;

public interface FinancialMpcKeyPort {

    String keygenWallet(UUID walletId, Long userId);
}
