package source.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import source.auth.AuthExceptions;
import source.auth.application.infra.persistence.jpa.AdminAccessAttemptRepository;
import source.auth.application.service.admin.AdminAccessService;
import source.auth.dto.AdminLoginResponseDTO;
import source.auth.model.entity.AdminAccessAttemptEntity;
import source.auth.model.enums.AdminAccessAttemptStatus;
import source.common.infra.logging.LogSanitizer;

import java.time.LocalDateTime;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                .getDeclaredMethod("pollLogin", java.util.UUID.class, jakarta.servlet.http.HttpServletRequest.class)
                .isAnnotationPresent(PreAuthorize.class));
    }

    @Test
    void pollLoginPassesRequestContextToService() {
        AdminAccessService service = mock(AdminAccessService.class);
        AdminAccessController controller = new AdminAccessController(service);
        UUID attemptId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        request.addHeader("User-Agent", "Desktop Browser");
        when(service.pollLogin(eq(attemptId), eq("203.0.113.10"), eq("Desktop Browser")))
                .thenReturn(new AdminLoginResponseDTO(
                        "PENDING",
                        true,
                        attemptId,
                        LocalDateTime.now().plusMinutes(5),
                        null,
                        "Aguardando autorizacao no app mobile."));

        controller.pollLogin(attemptId, request);

        verify(service).pollLogin(attemptId, "203.0.113.10", "Desktop Browser");
    }

    @Test
    void pollLoginRejectsMismatchedRequestContext() {
        AdminAccessAttemptRepository attempts = mock(AdminAccessAttemptRepository.class);
        AdminAccessService service = new AdminAccessService(
                null,
                null,
                null,
                null,
                null,
                attempts,
                null,
                null);
        UUID attemptId = UUID.randomUUID();
        AdminAccessAttemptEntity attempt = new AdminAccessAttemptEntity();
        attempt.setStatus(AdminAccessAttemptStatus.PENDING);
        attempt.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        attempt.setIpFingerprint(LogSanitizer.fingerprint("203.0.113.10"));
        attempt.setUserAgent("Desktop Browser");
        when(attempts.findForPollingById(attemptId)).thenReturn(Optional.of(attempt));

        AuthExceptions.StructuredAuthException exception = assertThrows(
                AuthExceptions.StructuredAuthException.class,
                () -> service.pollLogin(attemptId, "198.51.100.7", "Desktop Browser"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("ADMIN_ATTEMPT_CONTEXT_MISMATCH", exception.getErrorCode());
    }

    private void assertAdminOnly(Method method) {
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize, method.getName() + " must require admin role");
        assertEquals("hasRole('ADMIN')", preAuthorize.value(), method.getName());
    }
}
