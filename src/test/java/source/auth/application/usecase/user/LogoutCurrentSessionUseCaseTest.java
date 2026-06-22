package source.auth.application.usecase.user;

import org.junit.jupiter.api.Test;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class LogoutCurrentSessionUseCaseTest {

    private final JwtServicer jwtService = mock(JwtServicer.class);
    private final LogoutCurrentSessionUseCase useCase = new LogoutCurrentSessionUseCase(jwtService);

    @Test
    void executeRevokesTrimmedBearerToken() {
        LogoutCurrentSessionUseCase.Result result = useCase.execute("Bearer token-1   ");

        assertEquals(LogoutCurrentSessionUseCase.Status.REVOKED, result.status());
        verify(jwtService).revokeSession("token-1");
    }

    @Test
    void executeRejectsMissingBearerToken() {
        LogoutCurrentSessionUseCase.Result result = useCase.execute(null);

        assertEquals(LogoutCurrentSessionUseCase.Status.MISSING_TOKEN, result.status());
        verifyNoInteractions(jwtService);
    }

    @Test
    void executeRejectsBlankBearerToken() {
        LogoutCurrentSessionUseCase.Result result = useCase.execute("Bearer   ");

        assertEquals(LogoutCurrentSessionUseCase.Status.MISSING_TOKEN, result.status());
        verifyNoInteractions(jwtService);
    }

    @Test
    void executeSanitizesRevocationFailure() {
        doThrow(new IllegalArgumentException("raw jwt parse failure"))
                .when(jwtService).revokeSession("bad-token");

        LogoutCurrentSessionUseCase.Result result = useCase.execute("Bearer bad-token");

        assertEquals(LogoutCurrentSessionUseCase.Status.REVOCATION_FAILED, result.status());
        verify(jwtService).revokeSession("bad-token");
    }
}
