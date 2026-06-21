package source.auth.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import source.auth.AuthExceptions;
import source.auth.application.service.account.BackupCodeService;
import source.auth.application.service.identityaccess.TransactionalAuthenticationPort;
import source.auth.application.service.identityaccess.TransactionalAuthenticationRequest;
import source.auth.application.service.identityaccess.TransactionalAuthenticationScope;
import source.auth.dto.BackupCodesStatusDTO;
import source.common.dto.ApiResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackupCodesControllerTest {

    private final BackupCodeService backupCodeService = mock(BackupCodeService.class);
    private final TransactionalAuthenticationPort transactionalAuthenticationPort =
            mock(TransactionalAuthenticationPort.class);
    private final BackupCodesController controller =
            new BackupCodesController(backupCodeService, transactionalAuthenticationPort);

    @Test
    void regenerateRequiresStepUpBeforeRegeneratingBackupCodes() {
        Authentication authentication = authentication("42");
        BackupCodesStatusDTO status = new BackupCodesStatusDTO(true, 10, List.of("12345678"));
        when(backupCodeService.regenerate(42L)).thenReturn(status);

        ResponseEntity<ApiResponse<BackupCodesStatusDTO>> response = controller.regenerate(
                authentication,
                new BackupCodesController.RegenerateBackupCodesRequest(
                        "123456",
                        "{\"credentialId\":\"cred\"}",
                        "shares-confirmed"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(status, response.getBody().getData());

        InOrder inOrder = inOrder(transactionalAuthenticationPort, backupCodeService);
        inOrder.verify(transactionalAuthenticationPort).authorize(any(TransactionalAuthenticationRequest.class));
        inOrder.verify(backupCodeService).regenerate(42L);

        ArgumentCaptor<TransactionalAuthenticationRequest> captor =
                ArgumentCaptor.forClass(TransactionalAuthenticationRequest.class);
        verify(transactionalAuthenticationPort).authorize(captor.capture());
        assertEquals(42L, captor.getValue().authenticatedUserId());
        assertEquals(42L, captor.getValue().resourceOwnerUserId());
        assertEquals("123456", captor.getValue().totpCode());
        assertEquals("{\"credentialId\":\"cred\"}", captor.getValue().passkeyAssertionJson());
        assertEquals("shares-confirmed", captor.getValue().confirmationPassphrase());
        assertEquals(TransactionalAuthenticationScope.ACCOUNT_SECURITY_CHANGE, captor.getValue().scope());
    }

    @Test
    void regenerateDoesNotRegenerateBackupCodesWhenStepUpFails() {
        Authentication authentication = authentication("42");
        doThrow(new AuthExceptions.InvalidCredentials("raw provider failure"))
                .when(transactionalAuthenticationPort)
                .authorize(any(TransactionalAuthenticationRequest.class));

        AuthExceptions.InvalidCredentials exception = assertThrows(
                AuthExceptions.InvalidCredentials.class,
                () -> controller.regenerate(
                        authentication,
                        new BackupCodesController.RegenerateBackupCodesRequest(null, null, null)));

        assertEquals("Unable to authorize backup code regeneration.", exception.getMessage());
        verify(backupCodeService, never()).regenerate(42L);
    }

    private Authentication authentication(String name) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(name);
        return authentication;
    }
}
