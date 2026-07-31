package com.kerosene.common.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * No-op admin service implementations for standalone auth-service.
 * Real implementations are provided by kerosene-app (monolith) or kfe-service.
 */
final class NoopAdminServices {

    @Service
    @ConditionalOnMissingBean(value = AdminOnrampService.class, ignored = NoopAdminOnrampService.class)
    static class NoopAdminOnrampService implements AdminOnrampService {
        @Override
        public OnrampOrderDetail findOrder(String id) {
            throw new UnsupportedOperationException("Admin onramp not available in auth-service");
        }
    }

    @Service
    @ConditionalOnMissingBean(value = AdminReconciliationService.class, ignored = NoopAdminReconciliationService.class)
    static class NoopAdminReconciliationService implements AdminReconciliationService {
        @Override
        public ReconciliationStatus status() {
            return new ReconciliationStatus("NOT_AVAILABLE", Instant.EPOCH, 0, 0, 0, "auth-service standalone");
        }
    }

    @Service
    @ConditionalOnMissingBean(value = AdminProviderService.class, ignored = NoopAdminProviderService.class)
    static class NoopAdminProviderService implements AdminProviderService {
        @Override
        public ProviderValidationResult validateConnection(String connectionId) {
            return new ProviderValidationResult(connectionId, "unavailable", false, false,
                    0, Instant.EPOCH, "Admin provider check not available in auth-service");
        }
    }

    @Service
    @ConditionalOnMissingBean(value = AdminP2pService.class, ignored = NoopAdminP2pService.class)
    static class NoopAdminP2pService implements AdminP2pService {
        @Override
        public P2pOrderDetail findOrder(String id) {
            throw new UnsupportedOperationException("Admin P2P not available in auth-service");
        }
    }
}
