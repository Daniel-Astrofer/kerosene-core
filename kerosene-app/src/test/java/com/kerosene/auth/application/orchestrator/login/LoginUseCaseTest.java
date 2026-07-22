package source.auth.application.orchestrator.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.entity.UserDataBase;

class LoginUseCaseTest {

    private final StartLogin startLogin = mock(StartLogin.class);
    private final VerifySecondFactor verifySecondFactor = mock(VerifySecondFactor.class);
    private final IssueSessionToken issueSessionToken = mock(IssueSessionToken.class);
    private final LoginUseCase useCase = new LoginUseCase(startLogin, verifySecondFactor, issueSessionToken);

    @Test
    void loginUserShouldDelegateToStartLogin() {
        UserDTOContract dto = mock(UserDTOContract.class);
        when(startLogin.start(dto)).thenReturn("pre-auth-token");

        String result = useCase.loginUser(dto);

        assertEquals("pre-auth-token", result);
        verify(startLogin).start(dto);
    }

    @Test
    void loginTotpVerifyShouldVerifySecondFactorBeforeIssuingSession() {
        UserDTOContract dto = mock(UserDTOContract.class);
        UserDataBase user = new UserDataBase();
        when(verifySecondFactor.verify(dto)).thenReturn(user);
        when(issueSessionToken.issue(user)).thenReturn("7 jwt-token");

        String result = useCase.loginTotpVerify(dto);

        assertEquals("7 jwt-token", result);
        verify(verifySecondFactor).verify(dto);
        verify(issueSessionToken).issue(user);
    }
}
