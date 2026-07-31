package com.kerosene.common.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

@Configuration
public class NoopAdminServices {

    @Bean
    @ConditionalOnMissingBean(AdminOnrampService.class)
    AdminOnrampService noopAdminOnrampService() {
        return id -> {
            throw new UnsupportedOperationException("Admin onramp not available in auth-service");
        };
    }

    @Bean
    @ConditionalOnMissingBean(AdminReconciliationService.class)
    AdminReconciliationService noopAdminReconciliationService() {
        return () -> new AdminReconciliationService.ReconciliationStatus(
                "NOT_AVAILABLE", Instant.EPOCH, 0, 0, 0, "auth-service standalone");
    }

    @Bean
    @ConditionalOnMissingBean(AdminProviderService.class)
    AdminProviderService noopAdminProviderService() {
        return connectionId -> new AdminProviderService.ProviderValidationResult(
                connectionId, "unavailable", false, false,
                0, Instant.EPOCH, "Admin provider check not available in auth-service");
    }

    @Bean
    @ConditionalOnMissingBean(AdminP2pService.class)
    AdminP2pService noopAdminP2pService() {
        return id -> {
            throw new UnsupportedOperationException("Admin P2P not available in auth-service");
        };
    }

    @Bean
    @ConditionalOnMissingBean(AdminLedgerService.class)
    AdminLedgerService noopAdminLedgerService() {
        return new AdminLedgerService() {
            @Override
            public LedgerAccountDetail findAccount(String id) {
                throw new UnsupportedOperationException("Admin ledger not available in auth-service");
            }

            @Override
            public LedgerJournalDetail findJournal(String id) {
                throw new UnsupportedOperationException("Admin ledger not available in auth-service");
            }
        };
    }
}
