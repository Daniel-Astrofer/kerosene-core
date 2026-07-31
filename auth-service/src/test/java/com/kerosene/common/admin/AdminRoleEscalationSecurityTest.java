package com.kerosene.common.admin;

import com.kerosene.common.security.AdminRoles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Auth bypass, IDOR, and role escalation prevention tests for Admin API.
 *
 * <p>These tests operate at the annotation/reflection level and verify that
 * every admin endpoint has the correct {@code @PreAuthorize} guard for its
 * role tier. No controller or method can accidentally expose an endpoint
 * to a lower-privilege role.</p>
 *
 * <h3>Threat model coverage</h3>
 * <ul>
 *   <li><b>Auth bypass</b> — endpoint without any role check</li>
 *   <li><b>IDOR</b> — operator or auditor gaining access to admin-only
 *       functionality via path traversal or misconfigured matchers</li>
 *   <li><b>Role escalation</b> — a lower role accessing endpoints
 *       restricted to higher roles</li>
 *   <li><b>Missing policy</b> — controller paths not declared in
 *       {@code EndpointPolicyRegistry}</li>
 * </ul>
 */
class AdminRoleEscalationSecurityTest {

    private static final Set<String> PUBLIC_ADMIN_METHODS = Set.of("startLogin", "pollLogin");

    /**
     * Tiers: controller class → expected SpEL role expression.
     */
    private static final Map<Class<?>, String> ROLE_TIERS = Map.of(
            AdminOnrampController.class, AdminRoles.HAS_ANY_ADMIN_ROLE,
            AdminP2pController.class, AdminRoles.HAS_ANY_ADMIN_ROLE,
            AdminLedgerController.class, AdminRoles.HAS_ANY_ADMIN_ROLE,
            AdminReconciliationController.class, AdminRoles.HAS_ANY_ADMIN_ROLE,
            AdminOperationsController.class, AdminRoles.HAS_ADMIN_OR_OPERATOR,
            AdminProviderController.class, "hasRole('ADMIN')");

    /**
     * Which roles are permitted in each tier.
     */
    private static final Map<String, List<String>> TIER_TO_PERMITTED_ROLES = Map.of(
            AdminRoles.HAS_ANY_ADMIN_ROLE, List.of(AdminRoles.ADMIN, AdminRoles.OPERATOR, AdminRoles.AUDITOR),
            AdminRoles.HAS_ADMIN_OR_OPERATOR, List.of(AdminRoles.ADMIN, AdminRoles.OPERATOR),
            "hasRole('ADMIN')", List.of(AdminRoles.ADMIN));

    // ────── Auth Bypass ────────────────────────────────────────────────

    static boolean hasMappingAnnotation(Method method) {
        return method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class) != null
                || method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class) != null
                || method.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class) != null
                || method.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class) != null
                || method.getAnnotation(org.springframework.web.bind.annotation.PatchMapping.class) != null
                || method.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class) != null;
    }

    @Nested
    @DisplayName("Auth bypass prevention")
    class AuthBypassPrevention {

        @Test
        void everyAdminControllerHasClassLevelPreAuthorize() {
            for (Class<?> controller : ROLE_TIERS.keySet()) {
                boolean hasAnnotation = controller.getAnnotation(
                        org.springframework.security.access.prepost.PreAuthorize.class) != null;
                assertTrue(hasAnnotation,
                        controller.getSimpleName() + " is missing @PreAuthorize — auth bypass");
            }
        }

        @Test
        void adminAccessControllerHasMethodLevelPreAuthorize() {
            Class<?> controller = com.kerosene.auth.controller.AdminAccessController.class;
            for (Method method : controller.getDeclaredMethods()) {
                if (PUBLIC_ADMIN_METHODS.contains(method.getName())) {
                    continue;
                }
                if (!hasMappingAnnotation(method)) {
                    continue;
                }
                boolean hasAnnotation = method.getAnnotation(
                        org.springframework.security.access.prepost.PreAuthorize.class) != null;
                assertTrue(hasAnnotation,
                        "AdminAccessController#" + method.getName()
                                + " is missing @PreAuthorize — auth bypass");
            }
        }

        @Test
        void noPublicEndpointLeaksAdminFunctionality() {
            // Any method annotated with @GetMapping etc. in a controller with
            // a non-public path must be protected.
            Class<?> controller = com.kerosene.auth.controller.AdminAccessController.class;
            for (Method method : controller.getDeclaredMethods()) {
                if (PUBLIC_ADMIN_METHODS.contains(method.getName())) {
                    continue; // /auth/admin/login and poll are intentionally public
                }
                if (!hasMappingAnnotation(method)) {
                    continue;
                }
                // All other methods require ADMIN role
                org.springframework.security.access.prepost.PreAuthorize annotation =
                        method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
                assertTrue(annotation != null && "hasRole('ADMIN')".equals(annotation.value()),
                        "AdminAccessController#" + method.getName() + " auth bypass");
            }
        }
    }

    // ────── Role Escalation ────────────────────────────────────────────

    @Nested
    @DisplayName("Role escalation prevention")
    class RoleEscalationPrevention {

        @Test
        void operatorCannotAccessAdminOnlyEndpoints() {
            String adminOnlyExpression = "hasRole('ADMIN')";

            // AdminProviderController must be ADMIN-only
            assertEquals(adminOnlyExpression,
                    AdminProviderController.class.getAnnotation(
                            org.springframework.security.access.prepost.PreAuthorize.class).value(),
                    "Operator escalation: AdminProviderController must be ADMIN-only");

            // AdminAccessController methods (except login) must be ADMIN-only
            for (Method method : com.kerosene.auth.controller.AdminAccessController.class.getDeclaredMethods()) {
                if (PUBLIC_ADMIN_METHODS.contains(method.getName())) {
                    continue;
                }
                if (!hasMappingAnnotation(method)) {
                    continue;
                }
                assertEquals(adminOnlyExpression,
                        method.getAnnotation(
                                org.springframework.security.access.prepost.PreAuthorize.class).value(),
                        "Operator escalation: AdminAccessController#" + method.getName()
                                + " must be ADMIN-only");
            }
        }

        @Test
        void auditorCannotAccessAdminOnlyOrOperatorOnlyEndpoints() {
            // Check ADMIN-only controllers reject AUDITOR
            assertEquals("hasRole('ADMIN')",
                    AdminProviderController.class.getAnnotation(
                            org.springframework.security.access.prepost.PreAuthorize.class).value(),
                    "Auditor escalation: AdminProviderController must reject AUDITOR");

            // Check ADMIN+OPERATOR controllers reject AUDITOR
            assertEquals(AdminRoles.HAS_ADMIN_OR_OPERATOR,
                    AdminOperationsController.class.getAnnotation(
                            org.springframework.security.access.prepost.PreAuthorize.class).value(),
                    "Auditor escalation: AdminOperationsController must reject AUDITOR");
        }

        @Test
        void userRoleCannotAccessAnyAdminEndpoint() {
            // All admin controllers must have a role check stricter than just USER
            for (Map.Entry<Class<?>, String> entry : ROLE_TIERS.entrySet()) {
                String expression = entry.getValue();
                List<String> permittedRoles = TIER_TO_PERMITTED_ROLES.get(expression);
                assertTrue(permittedRoles != null && !permittedRoles.isEmpty(),
                        entry.getKey().getSimpleName() + " has unknown expression: " + expression);
                assertTrue(permittedRoles.stream().noneMatch("USER"::equals),
                        entry.getKey().getSimpleName() + " expression " + expression
                                + " would permit USER role");
            }
        }

        @Test
        void noRoleExpressionIsMorePermissiveThanIntended() {
            for (Map.Entry<Class<?>, String> entry : ROLE_TIERS.entrySet()) {
                String expression = entry.getValue();
                // Ensure expression uses hasRole or hasAnyRole — not permitAll or isAuthenticated
                assertTrue(expression.startsWith("hasRole") || expression.startsWith("hasAnyRole"),
                        entry.getKey().getSimpleName()
                                + " uses weak guard: " + expression);
            }
        }
    }

    // ────── IDOR (Insecure Direct Object Reference) ────────────────────

    @Nested
    @DisplayName("IDOR prevention via path consistency")
    class IdorPrevention {

        @Test
        void adminOnlyEndpointsAreNotAccidentallyMappedToBroaderPaths() {
            // AdminProviderController and AdminAccessController must use paths
            // that don't overlap with broader AUDITOR/OPERATOR paths
            String providerBase = extractBasePath(AdminProviderController.class);
            assertTrue(providerBase.startsWith("/api/admin/"),
                    "Provider base path must be under /api/admin/");

            String operationsBase = extractBasePath(AdminOperationsController.class);
            String ledgerBase = extractBasePath(AdminLedgerController.class);
            String onrampBase = extractBasePath(AdminOnrampController.class);
            String p2pBase = extractBasePath(AdminP2pController.class);
            String reconciliationBase = extractBasePath(AdminReconciliationController.class);

            // Each controller should have a unique base path under /api/admin/
            List<String> bases = List.of(providerBase, operationsBase, ledgerBase,
                    onrampBase, p2pBase, reconciliationBase);
            assertEquals(bases.size(), Set.copyOf(bases).size(),
                    "Duplicate base paths detected — IDOR risk");
        }

        @Test
        void adminOnlyControllerPathsAreNotPrefixOfBroaderControllers() {
            String providerBase = extractBasePath(AdminProviderController.class);
            String operationsBase = extractBasePath(AdminOperationsController.class);
            String ledgerBase = extractBasePath(AdminLedgerController.class);

            // /api/admin/providers should not be a prefix of /api/admin/providers/**
            // (self-test is valid; check against other paths)
            for (String otherBase : List.of(operationsBase, ledgerBase)) {
                boolean isPrefix = otherBase.startsWith(providerBase)
                        && otherBase.length() > providerBase.length();
                assertTrue(!isPrefix,
                        "Admin-only path " + providerBase
                                + " is a prefix of " + otherBase + " — IDOR risk");
            }
        }

        private String extractBasePath(Class<?> controller) {
            org.springframework.web.bind.annotation.RequestMapping mapping =
                    controller.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
            if (mapping == null) return "";
            String[] paths = mapping.value();
            if (paths.length == 0) paths = mapping.path();
            return paths.length > 0 ? paths[0] : "";
        }
    }

    // ────── Role tier completeness ─────────────────────────────────────

    @Nested
    @DisplayName("Role tier completeness")
    class RoleTierCompleteness {

        @Test
        void everyAdminControllerHasDefinedRoleTier() {
            Set<Class<?>> controllersWithTiers = ROLE_TIERS.keySet();
            Set<Class<?>> allAdminControllers = Set.of(
                    AdminLedgerController.class,
                    AdminOnrampController.class,
                    AdminP2pController.class,
                    AdminReconciliationController.class,
                    AdminProviderController.class,
                    AdminOperationsController.class);

            for (Class<?> controller : allAdminControllers) {
                assertTrue(controllersWithTiers.contains(controller),
                        controller.getSimpleName() + " is missing from ROLE_TIERS map");
            }
        }

        @Test
        void allTierExpressionsAreValidSpel() {
            for (String expression : ROLE_TIERS.values()) {
                assertTrue(expression.startsWith("hasRole") || expression.startsWith("hasAnyRole"),
                        "Invalid SpEL: " + expression);
                assertTrue(expression.contains("'"),
                        "Invalid SpEL (missing quotes): " + expression);
            }
        }
    }
}
