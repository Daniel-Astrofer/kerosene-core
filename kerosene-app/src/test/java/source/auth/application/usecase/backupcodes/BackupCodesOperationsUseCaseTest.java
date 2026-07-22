package source.auth.application.usecase.backupcodes;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import source.auth.AuthExceptions;
import source.auth.application.service.account.BackupCodeService;
import source.auth.application.service.identityaccess.TransactionalAuthenticationPort;
import source.auth.application.service.identityaccess.TransactionalAuthenticationRequest;
import source.auth.application.service.identityaccess.TransactionalAuthenticationScope;
import source.auth.dto.BackupCodesStatusDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BackupCodesOperationsUseCaseTest {

    private final BackupCodeService backupCodeService = mock(BackupCodeService.class);
    private final TransactionalAuthenticationPort transactionalAuthenticationPort =
            mock(TransactionalAuthenticationPort.class);
    private final BackupCodesOperationsUseCase useCase =
            new BackupCodesOperationsUseCase(backupCodeService, transactionalAuthenticationPort);

    @Test
    void getStatusDelegatesToBackupCodeService() {
        BackupCodesStatusDTO status = new BackupCodesStatusDTO(true, 4, List.of());
        when(backupCodeService.getStatus(42L)).thenReturn(status);

        BackupCodesStatusDTO result = useCase.getStatus(42L);

        assertSame(status, result);
        verify(backupCodeService).getStatus(42L);
        verifyNoInteractions(transactionalAuthenticationPort);
    }

    @Test
    void regenerateRequiresStepUpBeforeRegeneratingBackupCodes() {
        BackupCodesStatusDTO status = new BackupCodesStatusDTO(true, 10, List.of("12345678"));
        when(backupCodeService.regenerate(42L)).thenReturn(status);

        BackupCodesStatusDTO result = useCase.regenerate(
                42L,
                "123456",
                "{\"credentialId\":\"cred\"}",
                "shares-confirmed");

        assertSame(status, result);

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
        doThrow(new AuthExceptions.InvalidCredentials("raw provider failure"))
                .when(transactionalAuthenticationPort)
                .authorize(any(TransactionalAuthenticationRequest.class));

        AuthExceptions.InvalidCredentials exception = assertThrows(
                AuthExceptions.InvalidCredentials.class,
                () -> useCase.regenerate(42L, null, null, null));

        assertEquals("Unable to authorize backup code regeneration.", exception.getMessage());
        verify(backupCodeService, never()).regenerate(42L);
    }
}
