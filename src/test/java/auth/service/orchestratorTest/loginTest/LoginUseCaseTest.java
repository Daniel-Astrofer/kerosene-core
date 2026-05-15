/*package auth.service.orchestratorTest.loginTest;

import jakarta.servlet.http.HttpServletRequest;
import source.auth.application.infra.persistance.jpa.UserRepository;
import source.auth.application.orchestrator.login.LoginUseCase;
import source.auth.application.service.authentication.contracts.LoginVerifier;
import source.auth.application.service.device.UserDeviceService;
import source.auth.dto.UserDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {


    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private LoginUseCase login;

    @Test
    @DisplayName("should return id when user send jwt token")
    void should_return_id_when_jwtToken_send(){

        try(MockedStatic<SecurityContextHolder> se  = mockStatic(SecurityContextHolder.class) ){
            Authentication auth = mock(Authentication.class);
            SecurityContext context = mock(SecurityContext.class);

            when(context.getAuthentication()).thenReturn(auth);
            se.when(SecurityContextHolder::getContext).thenReturn(context);
            when(auth.getName()).thenReturn("1");


            UserDTO dto = new UserDTO();
            Assertions.assertEquals("1",login.loginUser(dto,request));
        }


    }


}
*/