package source.auth.application.orchestrator.login;

import org.springframework.stereotype.Component;

import source.auth.application.orchestrator.login.contracts.Login;
import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.entity.UserDataBase;

@Component
public class LoginUseCase implements Login {

    private final StartLogin startLogin;
    private final VerifySecondFactor verifySecondFactor;
    private final IssueSessionToken issueSessionToken;

    public LoginUseCase(StartLogin startLogin,
            VerifySecondFactor verifySecondFactor,
            IssueSessionToken issueSessionToken) {
        this.startLogin = startLogin;
        this.verifySecondFactor = verifySecondFactor;
        this.issueSessionToken = issueSessionToken;
    }

    @Override
    public String loginUser(UserDTOContract dto) {
        return startLogin.start(dto);
    }

    @Override
    public String loginTotpVerify(UserDTOContract dto) {
        UserDataBase user = verifySecondFactor.verify(dto);
        return issueSessionToken.issue(user);
    }
}
