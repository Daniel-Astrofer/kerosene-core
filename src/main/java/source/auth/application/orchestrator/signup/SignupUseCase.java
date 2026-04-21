package source.auth.application.orchestrator.signup;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import source.auth.application.orchestrator.login.contracts.Signup;
import source.auth.dto.SignupResponseDTO;
import source.auth.dto.UserDTO;

@Component
public class SignupUseCase implements Signup {

    private final StartSignup startSignup;
    private final VerifySignupTotp verifySignupTotp;
    private final FinalizeSignupOnPayment finalizeSignupOnPayment;

    public SignupUseCase(StartSignup startSignup,
            VerifySignupTotp verifySignupTotp,
            FinalizeSignupOnPayment finalizeSignupOnPayment) {
        this.startSignup = startSignup;
        this.verifySignupTotp = verifySignupTotp;
        this.finalizeSignupOnPayment = finalizeSignupOnPayment;
    }

    @Override
    public SignupResponseDTO signupUser(UserDTO dto) {
        return startSignup.execute(dto);
    }

    @Override
    public String createUser(UserDTO dto) {
        return verifySignupTotp.execute(dto);
    }

    public void finalizeUserFromRedis(String sessionId, String txid, BigDecimal amountPaid) {
        finalizeSignupOnPayment.execute(sessionId, txid, amountPaid);
    }
}
