package source.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import source.auth.application.usecase.backupcodes.BackupCodesOperationsUseCase;
import source.auth.dto.BackupCodesStatusDTO;
import source.common.dto.ApiResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackupCodesControllerTest {

    private final BackupCodesOperationsUseCase backupCodesOperationsUseCase =
            mock(BackupCodesOperationsUseCase.class);
    private final BackupCodesController controller =
            new BackupCodesController(backupCodesOperationsUseCase);

    @Test
    void getStatusDelegatesParsedUserIdAndMapsSuccessMessage() {
        Authentication authentication = authentication("42");
        BackupCodesStatusDTO status = new BackupCodesStatusDTO(true, 4, List.of());
        when(backupCodesOperationsUseCase.getStatus(42L)).thenReturn(status);

        ResponseEntity<ApiResponse<BackupCodesStatusDTO>> response = controller.getStatus(authentication);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Backup code status retrieved successfully.", response.getBody().getMessage());
        assertEquals(status, response.getBody().getData());
        verify(backupCodesOperationsUseCase).getStatus(42L);
    }

    @Test
    void regenerateDelegatesParsedUserIdAndMapsSuccessMessage() {
        Authentication authentication = authentication("42");
        BackupCodesStatusDTO status = new BackupCodesStatusDTO(true, 10, List.of("12345678"));
        when(backupCodesOperationsUseCase.regenerate(
                42L,
                "123456",
                "{\"credentialId\":\"cred\"}",
                "shares-confirmed"))
                .thenReturn(status);

        ResponseEntity<ApiResponse<BackupCodesStatusDTO>> response = controller.regenerate(
                authentication,
                new BackupCodesController.RegenerateBackupCodesRequest(
                        "123456",
                        "{\"credentialId\":\"cred\"}",
                        "shares-confirmed"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Backup codes regenerated successfully.", response.getBody().getMessage());
        assertEquals(status, response.getBody().getData());
        verify(backupCodesOperationsUseCase).regenerate(
                42L,
                "123456",
                "{\"credentialId\":\"cred\"}",
                "shares-confirmed");
    }

    @Test
    void regenerateNormalizesNullRequestToEmptyStepUpValues() {
        Authentication authentication = authentication("42");
        BackupCodesStatusDTO status = new BackupCodesStatusDTO(true, 10, List.of("12345678"));
        when(backupCodesOperationsUseCase.regenerate(42L, null, null, null)).thenReturn(status);

        ResponseEntity<ApiResponse<BackupCodesStatusDTO>> response = controller.regenerate(authentication, null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(status, response.getBody().getData());
        verify(backupCodesOperationsUseCase).regenerate(42L, null, null, null);
    }

    private Authentication authentication(String name) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(name);
        return authentication;
    }
}
