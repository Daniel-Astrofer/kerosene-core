package com.kerosene.auth.application.orchestrator.login;

import org.springframework.stereotype.Component;

import com.kerosene.auth.application.orchestrator.login.contracts.Login;
import com.kerosene.auth.dto.contracts.UserDTOContract;
import com.kerosene.auth.model.entity.UserDataBase;

/** Coordinates the primary credential and second-factor stages of the login flow. */
@Component
public class LoginUseCase implements Login {

    /** Validates credentials and creates either a session or a short-lived pre-auth token. */
    private final StartLogin startLogin;
    /** Verifies the required second factor for a pending login. */
    private final VerifySecondFactor verifySecondFactor;
    /** Issues the session response after authentication is complete. */
    private final IssueSessionToken issueSessionToken;

    /**
     * Creates the login coordinator from its three workflow steps.
     *
     * @param startLogin primary credential stage
     * @param verifySecondFactor pending second-factor stage
     * @param issueSessionToken authenticated session issuer
     */
    public LoginUseCase(StartLogin startLogin,
            VerifySecondFactor verifySecondFactor,
            IssueSessionToken issueSessionToken) {
        this.startLogin = startLogin;
        this.verifySecondFactor = verifySecondFactor;
        this.issueSessionToken = issueSessionToken;
    }

    /** Starts login with primary credentials; returns a session or a pre-authentication token. */
    /** @param dto submitted login data */
    /** @return session response when no second factor is required, otherwise pre-auth token */
    @Override
    public String loginUser(UserDTOContract dto) {
        return startLogin.start(dto);
    }

    /** Verifies the second factor and issues the authenticated session response. */
    /** @param dto pending login data containing the pre-auth token and factor */
    /** @return user ID and signed JWT response */
    @Override
    public String loginTotpVerify(UserDTOContract dto) {
        UserDataBase user = verifySecondFactor.verify(dto);
        return issueSessionToken.issue(user);
    }
}
