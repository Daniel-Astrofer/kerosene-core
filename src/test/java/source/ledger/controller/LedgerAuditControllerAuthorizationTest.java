package source.ledger.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LedgerAuditControllerAuthorizationTest {

    private static final Set<String> ADMIN_ONLY_METHODS = Set.of(
            "getTreasuryAuditConfig",
            "updateTreasuryAuditConfig",
            "siphonFees",
            "requestSiphonPayout",
            "approveSiphonPayout",
            "cancelSiphonPayout",
            "generateOperationalReserveProof");

    @Test
    void sensitiveAuditOperationsRequireAdminRole() {
        Set<String> protectedMethods = Arrays.stream(LedgerAuditController.class.getDeclaredMethods())
                .filter(method -> ADMIN_ONLY_METHODS.contains(method.getName()))
                .peek(this::assertAdminOnly)
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(ADMIN_ONLY_METHODS, protectedMethods);
    }

    @Test
    void transparencyStatsRemainAuthenticatedReadEndpoint() throws Exception {
        assertFalse(LedgerAuditController.class
                .getDeclaredMethod("getTransparencyStats")
                .isAnnotationPresent(PreAuthorize.class));
    }

    private void assertAdminOnly(Method method) {
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize, method.getName() + " must require admin role");
        assertEquals("hasRole('ADMIN')", preAuthorize.value(), method.getName());
    }
}
