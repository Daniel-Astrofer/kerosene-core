package source.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminAccessControllerAuthorizationTest {

    private static final Set<String> ADMIN_ONLY_METHODS = Set.of(
            "createOrRotateKey",
            "keyStatus",
            "revokeKey",
            "pendingAttempts",
            "decide",
            "devices",
            "blockDevice",
            "revokeDevice");

    @Test
    void adminManagementEndpointsRequireAdminRole() {
        Set<String> protectedMethods = Arrays.stream(AdminAccessController.class.getDeclaredMethods())
                .filter(method -> ADMIN_ONLY_METHODS.contains(method.getName()))
                .peek(this::assertAdminOnly)
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(ADMIN_ONLY_METHODS, protectedMethods);
    }

    @Test
    void loginPollingEndpointsRemainPublicEntryPoints() throws Exception {
        assertFalse(AdminAccessController.class
                .getDeclaredMethod("startLogin", source.auth.dto.AdminLoginRequestDTO.class,
                        jakarta.servlet.http.HttpServletRequest.class)
                .isAnnotationPresent(PreAuthorize.class));
        assertFalse(AdminAccessController.class
                .getDeclaredMethod("pollLogin", java.util.UUID.class)
                .isAnnotationPresent(PreAuthorize.class));
    }

    private void assertAdminOnly(Method method) {
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize, method.getName() + " must require admin role");
        assertEquals("hasRole('ADMIN')", preAuthorize.value(), method.getName());
    }
}
