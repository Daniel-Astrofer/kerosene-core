package com.kerosene.admin.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

/**
 * Supplies explicit standalone placeholders when optional administrative integrations are absent.
 * These beans do not claim successful external operations: they return NOT_AVAILABLE projections
 * or reject unsupported queries so API consumers can distinguish missing integrations.
 */
@Configuration
public class NoopAdminServices {

    /** Provides a conditional fallback that rejects onramp lookups. */
    @Bean
    @ConditionalOnMissingBean(AdminOnrampService.class)
    AdminOnrampService noopAdminOnrampService() {
        return id -> {
            throw new UnsupportedOperationException("Admin onramp not available in auth-service");
        };
    }

    /** Supplies a stable NOT_AVAILABLE reconciliation snapshot in standalone mode. */
    @Bean
    @ConditionalOnMissingBean(AdminReconciliationService.class)
    AdminReconciliationService noopAdminReconciliationService() {
        return () -> new AdminReconciliationService.ReconciliationStatus(
                "NOT_AVAILABLE", Instant.EPOCH, 0, 0, 0, "auth-service standalone");
    }

    /** Supplies an unavailable provider validation result when no real adapter is configured. */
    @Bean
    @ConditionalOnMissingBean(AdminProviderService.class)
    AdminProviderService noopAdminProviderService() {
        return connectionId -> new AdminProviderService.ProviderValidationResult(
                connectionId, "unavailable", false, false,
                0, Instant.EPOCH, "Admin provider check not available in auth-service");
    }

    /** Provides a conditional fallback that rejects P2P order lookups. */
    @Bean
    @ConditionalOnMissingBean(AdminP2pService.class)
    AdminP2pService noopAdminP2pService() {
        return id -> {
            throw new UnsupportedOperationException("Admin P2P not available in auth-service");
        };
    }

    /** Supplies explicit unavailable account/journal projections for optional ledger integration. */
    @Bean
    @ConditionalOnMissingBean(AdminLedgerService.class)
    AdminLedgerService noopAdminLedgerService() {
        return new UnavailableAdminLedgerService();
    }

    /** Typed fallback implementation for ledger queries when the integration bean is missing. */
    private static final class UnavailableAdminLedgerService implements AdminLedgerService {

        /**
         * Returns an explicit zero-valued unavailable account view.
         *
         * @param id requested ledger account identifier
         * @return account projection with UNAVAILABLE status
         */
        @Override
        public LedgerAccountDetail findAccount(String id) {
            return new LedgerAccountDetail(id, "unknown", "BTC", "0",
                    "UNAVAILABLE", 0L, 0L, java.util.List.of());
        }

        /**
         * Returns an explicit unavailable journal view without fabricating a posting.
         *
         * @param id requested journal identifier
         * @return journal projection with UNAVAILABLE status
         */
        @Override
        public LedgerJournalDetail findJournal(String id) {
            return new LedgerJournalDetail(id, "unknown", "unknown", "0",
                    "BTC", "ledger unavailable in auth-service standalone",
                    "unknown", 0L, "UNAVAILABLE");
        }
    }
}
