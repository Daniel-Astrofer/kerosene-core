package com.kerosene.auth.application.orchestrator.signup;

import org.springframework.stereotype.Component;

import com.kerosene.auth.application.orchestrator.login.contracts.Signup;
import com.kerosene.auth.dto.SignupResponseDTO;
import com.kerosene.auth.dto.UserDTO;

/** Implements the signup application contract by delegating initiation and TOTP confirmation. */
@Component
public class SignupUseCase implements Signup {

    /** Signup initializer that validates input and stores pending state. */
    private final StartSignup startSignup;
    /** TOTP confirmation operation that updates pending signup state. */
    private final VerifySignupTotp verifySignupTotp;

    /** Creates the signup use-case coordinator. */
    /** @param startSignup signup initiation step */
    /** @param verifySignupTotp TOTP confirmation step */
    public SignupUseCase(StartSignup startSignup,
            VerifySignupTotp verifySignupTotp) {
        this.startSignup = startSignup;
        this.verifySignupTotp = verifySignupTotp;
    }

    /** Delegates signup initialization and returns the enrollment response. */
    /** @param dto signup input */
    /** @return signup session and TOTP/backup-code setup response */
    @Override
    public SignupResponseDTO signupUser(UserDTO dto) {
        return startSignup.execute(dto);
    }

    /** Delegates signup TOTP verification and returns the session ID. */
    /** @param dto signup session and submitted TOTP code */
    /** @return signup session identifier */
    @Override
    public String createUser(UserDTO dto) {
        return verifySignupTotp.execute(dto);
    }
}
