package source.auth.service.orchestratorTest.signupTest;


import jakarta.servlet.http.HttpServletRequest;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.application.orchestrator.signup.SignupUseCase;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.device.UserDeviceService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.ip_handler.contracts.IP;
import source.auth.dto.UserDTO;
import source.auth.model.entity.UserDataBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class signupCreateUserTest {

    @Mock
    private RedisServicer cache;

    @Mock
    private UserServiceContract userService;

    @Mock
    private JwtServicer jwt;

    @Mock
    private UserDeviceService deviceService;

    @Mock
    private IP ip;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    SignupUseCase signup;

    @Test
    void should_createUser_when_totp_correct(){
        UserDTO dto = new UserDTO();
        dto.setUsername("testuser");
        dto.setPassphrase("Test@1234");
        dto.setTotpSecret("testsecret");

        UserDataBase data = new UserDataBase();
        data.setUsername(dto.getUsername());
        data.setPassphrase(dto.getPassphrase());
        data.setTOTPSecret(dto.getTotpSecret());
        ReflectionTestUtils.setField(data,"id",1L);

        when(ip.getIP(request)).thenReturn("123");
        when(cache.getFromRedis(dto)).thenReturn(dto);
        when(userService.fromDTO(dto)).thenReturn(data);
        when(jwt.generateToken(1,"devicehashtest")).thenReturn("yes");
        when(request.getHeader("X-Device-Hash")).thenReturn("devicehashtest");
        String code = signup.createUser(dto,request);

        Assertions.assertEquals("yes", code);

        verify(ip).getIP(request);
        verify(cache).getFromRedis(dto);
        verify(userService).fromDTO(dto);
        verify(jwt).generateToken(1,"devicehashtest");
        verify(request).getHeader("X-Device-Hash");


    }
}
