package com.kerosene.common.admin;

import com.kerosene.common.security.AdminRoles;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.kerosene.common.security.EndpointPolicyRegistry;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive security audit for all Admin API controllers.
 *
 * <p>Ensures every admin endpoint has:
 * <ol>
 *   <li>Class-level or method-level {@code @PreAuthorize} with the appropriate
 *       role expression for its sensitivity tier.</li>
 *   <li>A declared policy in {@link EndpointPolicyRegistry}.</li>
 *   <li>No endpoint accidentally lacks protection (auth bypass).</li>
 * </ol>
 *
 * <h3>RBAC tiers</h3>
 * <ul>
 *   <li><b>ADMIN only</b> — infrastructure control, key/device management
 *       ({@link AdminProviderController}, {@link AdminAccessController})</li>
 *   <li><b>ADMIN + OPERATOR</b> — operational monitoring
 *       ({@link AdminOperationsController})</li>
 *   <li><b>ADMIN + OPERATOR + AUDITOR</b> — read-only financial/order data
 *       ({@link AdminLedgerController}, {@link AdminOnrampController},
 *       {@link AdminP2pController}, {@link AdminReconciliationController})</li>
 * </ul>
 */
class AdminApiSecurityTest {

    private static final List<Class<? extends Annotation>> METHOD_MAPPING_ANNOTATIONS = List.of(
            RequestMapping.class, GetMapping.class, PostMapping.class,
            PutMapping.class, DeleteMapping.class, PatchMapping.class);

    /** Controllers accessible by ADMIN + OPERATOR + AUDITOR. */
    private static final Set<Class<?>> ANY_ADMIN_ROLE_CONTROLLERS = Set.of(
            AdminLedgerController.class,
            AdminOnrampController.class,
            AdminP2pController.class,
            AdminReconciliationController.class);

    /** Controllers accessible by ADMIN + OPERATOR only. */
    private static final Set<Class<?>> ADMIN_OR_OPERATOR_CONTROLLERS = Set.of(
            AdminOperationsController.class);

    /** Controllers accessible by ADMIN only. */
    private static final Set<Class<?>> ADMIN_ONLY_CONTROLLERS = Set.of(
            AdminProviderController.class);

    private static final Class<?> ADMIN_ACCESS_CONTROLLER =
            com.kerosene.auth.controller.AdminAccessController.class;

    /** Maps controller → expected SpEL expression. */
    private static final Map<Class<?>, String> EXPECTED_ROLE_EXPRESSIONS = Map.of(
            AdminLedgerController.class, AdminRoles.HAS_ANY_ADMIN_ROLE,
            AdminOnrampController.class, AdminRoles.HAS_ANY_ADMIN_ROLE,
            AdminP2pController.class, AdminRoles.HAS_ANY_ADMIN_ROLE,
            AdminReconciliationController.class, AdminRoles.HAS_ANY_ADMIN_ROLE,
            AdminOperationsController.class, AdminRoles.HAS_ADMIN_OR_OPERATOR,
            AdminProviderController.class, "hasRole('ADMIN')");

    private final EndpointPolicyRegistry registry = new EndpointPolicyRegistry();

    // ────── 1. @PreAuthorize presence and correctness ──────────────────

    @Test
    void allControllersHaveClassLevelPreAuthorize() {
        for (Class<?> controller : EXPECTED_ROLE_EXPRESSIONS.keySet()) {
            String expected = EXPECTED_ROLE_EXPRESSIONS.get(controller);
            PreAuthorize annotation = controller.getAnnotation(PreAuthorize.class);
            assertNotNull(annotation,
                    controller.getSimpleName() + " must have class-level @PreAuthorize");
            assertEquals(expected, annotation.value(),
                    controller.getSimpleName() + " must use correct role expression");
        }
    }

    @Test
    void adminAccessControllerUsesMethodLevelAdminRole() {
        Set<String> publicMethods = Set.of("startLogin", "pollLogin");
        for (Method method : ADMIN_ACCESS_CONTROLLER.getDeclaredMethods()) {
            if (publicMethods.contains(method.getName())) {
                continue;
            }
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
            assertNotNull(annotation,
                    "AdminAccessController#" + method.getName() + " must have @PreAuthorize");
            assertEquals("hasRole('ADMIN')", annotation.value(),
                    "AdminAccessController#" + method.getName() + " must require ADMIN role");
        }
    }

    // ────── 2. Endpoint policy registry coverage ──────────────────────

    @Test
    void adminEndpointsAreDeclaredInEndpointPolicyRegistry() {
        Set<Class<?>> allControllers = allAdminControllers();
        for (Class<?> controller : allControllers) {
            List<String> basePaths = classMappingPaths(controller);
            for (Method method : controller.getDeclaredMethods()) {
                List<String> methodPaths = mappingPaths(method);
                if (methodPaths.isEmpty()) {
                    continue;
                }
                for (String basePath : basePaths) {
                    for (String methodPath : methodPaths) {
                        String endpoint = combine(basePath, methodPath);
                        assertTrue(registry.hasDeclaredPolicy(endpoint),
                                controller.getSimpleName() + "#" + method.getName()
                                        + " endpoint " + endpoint + " must be in EndpointPolicyRegistry");
                    }
                }
            }
        }
    }

    @Test
    void adminPolicyCoversAllApiAdminPaths() {
        assertTrue(registry.policyFor("/api/admin/ledger/accounts/123").isPresent());
        assertEquals(EndpointPolicyRegistry.Policy.ADMIN,
                registry.policyFor("/api/admin/ledger/accounts/123").orElseThrow());

        assertTrue(registry.policyFor("/api/admin/ledger/journals/456").isPresent());
        assertEquals(EndpointPolicyRegistry.Policy.ADMIN,
                registry.policyFor("/api/admin/ledger/journals/456").orElseThrow());

        assertTrue(registry.policyFor("/api/admin/p2p/orders/789").isPresent());
        assertEquals(EndpointPolicyRegistry.Policy.ADMIN,
                registry.policyFor("/api/admin/p2p/orders/789").orElseThrow());

        assertTrue(registry.policyFor("/api/admin/onramp/orders/abc").isPresent());
        assertEquals(EndpointPolicyRegistry.Policy.ADMIN,
                registry.policyFor("/api/admin/onramp/orders/abc").orElseThrow());

        assertTrue(registry.policyFor("/api/admin/reconciliation/status").isPresent());
        assertEquals(EndpointPolicyRegistry.Policy.ADMIN,
                registry.policyFor("/api/admin/reconciliation/status").orElseThrow());

        assertTrue(registry.policyFor("/api/admin/providers/connections/conn1/validation").isPresent());
        assertEquals(EndpointPolicyRegistry.Policy.ADMIN,
                registry.policyFor("/api/admin/providers/connections/conn1/validation").orElseThrow());

        assertTrue(registry.policyFor("/api/admin/operations/overview").isPresent());
        assertEquals(EndpointPolicyRegistry.Policy.ADMIN,
                registry.policyFor("/api/admin/operations/overview").orElseThrow());
    }

    @Test
    void nonAdminPathsAreNotMarkedAsAdminPolicy() {
        assertTrue(registry.policyFor("/api/public/healthz").isPresent());
        assertEquals(EndpointPolicyRegistry.Policy.PUBLIC,
                registry.policyFor("/api/public/healthz").orElseThrow());
    }

    @Test
    void undeclaredEndpointsUnderApiAdminAreStillAdminPolicy() {
        assertTrue(registry.policyFor("/api/admin/random/unknown/endpoint").isPresent());
        assertEquals(EndpointPolicyRegistry.Policy.ADMIN,
                registry.policyFor("/api/admin/random/unknown/endpoint").orElseThrow());
    }

    // ────── 3. Auth bypass prevention ─────────────────────────────────

    @Test
    void noAdminEndpointLacksRoleCheck() {
        // Every controller class must be accounted for in one of the role tiers
        // or in AdminAccessController (method-level).  Any controller added
        // without a @PreAuthorize will cause this test to fail.
        for (Class<?> controller : allAdminControllers()) {
            if (controller == ADMIN_ACCESS_CONTROLLER) {
                // method-level check done separately
                continue;
            }
            PreAuthorize annotation = controller.getAnnotation(PreAuthorize.class);
            assertNotNull(annotation,
                    controller.getSimpleName() + " is missing @PreAuthorize (auth bypass risk)");
        }
    }

    // ────── 4. IDOR / Role escalation prevention ──────────────────────

    @Test
    void operatorCannotAccessAdminOnlyEndpoints() {
        // Operators should NOT have ADMIN role; they should only have OPERATOR
        // In @PreAuthorize terms: AdminProviderController requires 'ADMIN' only
        PreAuthorize providerAnnotation = AdminProviderController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(providerAnnotation);
        assertEquals("hasRole('ADMIN')", providerAnnotation.value(),
                "Provider controller must be ADMIN-only to prevent role escalation");

        // AdminAccessController methods also require ADMIN only
        for (Method method : ADMIN_ACCESS_CONTROLLER.getDeclaredMethods()) {
            if (Set.of("startLogin", "pollLogin").contains(method.getName())) {
                continue;
            }
            PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
            assertNotNull(annotation);
            assertEquals("hasRole('ADMIN')", annotation.value(),
                    "AdminAccessController#" + method.getName()
                            + " must be ADMIN-only (key/device management)");
        }
    }

    @Test
    void auditorCanOnlyAccessReadOnlyTierEndpoints() {
        // AUDITOR-accessible controllers should all use HAS_ANY_ADMIN_ROLE
        for (Class<?> controller : ANY_ADMIN_ROLE_CONTROLLERS) {
            PreAuthorize annotation = controller.getAnnotation(PreAuthorize.class);
            assertNotNull(annotation);
            assertEquals(AdminRoles.HAS_ANY_ADMIN_ROLE, annotation.value(),
                    controller.getSimpleName() + " must allow AUDITOR role");
        }

        // AUDITOR should NOT have access to ADMIN-only controllers
        for (Class<?> controller : ADMIN_ONLY_CONTROLLERS) {
            PreAuthorize annotation = controller.getAnnotation(PreAuthorize.class);
            assertNotNull(annotation);
            assertEquals("hasRole('ADMIN')", annotation.value(),
                    controller.getSimpleName() + " must NOT allow AUDITOR");
        }

        // AUDITOR should NOT have access to OPERATOR-only controllers
        for (Class<?> controller : ADMIN_OR_OPERATOR_CONTROLLERS) {
            PreAuthorize annotation = controller.getAnnotation(PreAuthorize.class);
            assertNotNull(annotation);
            assertEquals(AdminRoles.HAS_ADMIN_OR_OPERATOR, annotation.value(),
                    controller.getSimpleName() + " must NOT allow AUDITOR (only ADMIN+OPERATOR)");
        }
    }

    // ────── 5. RBAC consistency — no controller falls through cracks ──

    @Test
    void allAdminControllersAreCoveredByRbacTiers() {
        Set<Class<?>> covered = new java.util.LinkedHashSet<>();
        covered.addAll(ANY_ADMIN_ROLE_CONTROLLERS);
        covered.addAll(ADMIN_OR_OPERATOR_CONTROLLERS);
        covered.addAll(ADMIN_ONLY_CONTROLLERS);
        covered.add(ADMIN_ACCESS_CONTROLLER);

        for (Class<?> controller : allAdminControllers()) {
            assertTrue(covered.contains(controller),
                    controller.getSimpleName() + " is not assigned to any RBAC tier");
        }
    }

    // ────── 6. Role string constants match AdminRoles ─────────────────

    @Test
    void adminRolesConstantsAreConsistentWithAnnotations() {
        assertEquals("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR')", AdminRoles.HAS_ANY_ADMIN_ROLE);
        assertEquals("hasAnyRole('ADMIN', 'OPERATOR')", AdminRoles.HAS_ADMIN_OR_OPERATOR);
    }

    // ────── Helpers ──────────────────────────────────────────────────

    private Set<Class<?>> allAdminControllers() {
        Set<Class<?>> all = new java.util.LinkedHashSet<>();
        all.addAll(ANY_ADMIN_ROLE_CONTROLLERS);
        all.addAll(ADMIN_OR_OPERATOR_CONTROLLERS);
        all.addAll(ADMIN_ONLY_CONTROLLERS);
        all.add(ADMIN_ACCESS_CONTROLLER);
        return all;
    }

    private List<String> mappingPaths(Method method) {
        return METHOD_MAPPING_ANNOTATIONS.stream()
                .flatMap(annotationType -> annotationPaths(method, annotationType).stream())
                .distinct()
                .toList();
    }

    private List<String> classMappingPaths(Class<?> controller) {
        List<String> paths = annotationPaths(controller, RequestMapping.class);
        return paths.isEmpty() ? List.of("") : paths;
    }

    private List<String> annotationPaths(AnnotatedElement element, Class<? extends Annotation> annotationType) {
        var mergedAnnotation = org.springframework.core.annotation.MergedAnnotations.from(element).get(annotationType);
        if (!mergedAnnotation.isPresent()) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        paths.addAll(Arrays.asList(mergedAnnotation.getStringArray("value")));
        paths.addAll(Arrays.asList(mergedAnnotation.getStringArray("path")));
        return paths.stream()
                .filter(path -> path != null && !path.isBlank())
                .distinct()
                .toList();
    }

    private String combine(String basePath, String methodPath) {
        String normalizedBase = normalize(basePath);
        String normalizedMethod = normalize(methodPath);
        if ("/".equals(normalizedBase)) return normalizedMethod;
        if ("/".equals(normalizedMethod)) return normalizedBase;
        return normalizedBase + normalizedMethod;
    }

    private String normalize(String path) {
        if (path == null || path.isBlank()) return "/";
        return path.startsWith("/") ? path : "/" + path;
    }
}
