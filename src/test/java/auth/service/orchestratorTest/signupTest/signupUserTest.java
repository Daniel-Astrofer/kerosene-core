package source.auth.service.orchestratorTest.signupTest;



import jakarta.servlet.http.HttpServletRequest;
import source.auth.application.orchestrator.signup.SignupUseCase;
import source.auth.application.service.authentication.contracts.SignupVerifier;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.validation.totp.contratcs.TOTPKeyGenerate;
import source.auth.dto.UserDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;




import static org.mockito.Mockito.when;

/*@ExtendWith(MockitoExtension.class)
public class signupUserTest {

    @Mock
    private TOTPKeyGenerate totpKeyGenerate;

    @Mock
    RedisService cacheService;

    @Mock
    private SignupVerifier verifier;

    @InjectMocks
    private SignupUseCase signup;

    @Test
    void should_return_OTPUri_when_valid_credentials(){

        String secret = totpKeyGenerate.keyGenerator();
        String otp = "otpauth://totp/Kerosene:Test?secret="+ secret +"&issuer=Kerosene";
        UserDTO dto = new UserDTO();
        dto.setUsername("Test");
        dto.setPassphrase("assault walk plastic puppy staff cushion primary parrot distance physical daughter rescue loop disagree hill abstract axis betray");
        dto.setTotpSecret(secret);

        when(verifier.verify(dto.getUsername(),dto.getPassphrase())).thenReturn(true);

        Assertions.assertEquals(otp ,signup.signupUser(dto));
    }

}
*/