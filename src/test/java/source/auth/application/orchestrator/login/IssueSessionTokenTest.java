package source.auth.application.orchestrator.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;

import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.model.entity.UserDataBase;
import source.notification.service.NotificationService;

class IssueSessionTokenTest {

    @Test
    void issueShouldGenerateJwtAndNotifyUser() {
        JwtServicer jwtService = mock(JwtServicer.class);
        NotificationService notificationService = mock(NotificationService.class);
        IssueSessionToken issueSessionToken = new IssueSessionToken(jwtService, notificationService);

        UserDataBase user = new UserDataBase();
        ReflectionTestUtils.setField(user, "id", 7L);
        when(jwtService.generateToken(eq(7L), anyCollection())).thenReturn("jwt-token");

        String result = issueSessionToken.issue(user);

        assertEquals("7 jwt-token", result);
        verify(notificationService).notifyUser(
                eq(7L),
                eq(NotificationKind.SECURITY_LOGIN_DETECTED),
                eq(NotificationSeverity.WARNING),
                eq("Acesso Detectado"),
                contains("novo acesso"),
                eq("/settings"),
                eq("user"),
                eq("7"),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
