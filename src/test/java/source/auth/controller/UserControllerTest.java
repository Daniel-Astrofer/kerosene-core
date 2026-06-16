package source.auth.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import source.auth.application.orchestrator.login.contracts.Login;
import source.auth.application.orchestrator.login.contracts.Signup;
import source.auth.application.service.pow.PowService;
import source.auth.dto.UserDTO;
import source.common.exception.GlobalExceptionHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private final Login login = mock(Login.class);
    private final Signup signup = mock(Signup.class);
    private final PowService powService = mock(PowService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new UserController(login, signup, powService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void signupTotpVerifyAcceptsSessionPayloadWithoutUsername() throws Exception {
        when(signup.createUser(any(UserDTO.class))).thenReturn("session-1");

        mockMvc.perform(post("/auth/signup/totp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"session-1\",\"totpCode\":\"123456\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data").value("session-1"));

        ArgumentCaptor<UserDTO> captor = ArgumentCaptor.forClass(UserDTO.class);
        verify(signup).createUser(captor.capture());
        assertEquals("session-1", captor.getValue().getSessionId());
        assertEquals("123456", captor.getValue().getTotpCode());
    }

    @Test
    void signupTotpVerifyAcceptsSkipPayloadWithOnlySessionId() throws Exception {
        when(signup.createUser(any(UserDTO.class))).thenReturn("session-1");

        mockMvc.perform(post("/auth/signup/totp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"session-1\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data").value("session-1"));

        ArgumentCaptor<UserDTO> captor = ArgumentCaptor.forClass(UserDTO.class);
        verify(signup).createUser(captor.capture());
        assertEquals("session-1", captor.getValue().getSessionId());
        assertNull(captor.getValue().getTotpCode());
    }

    @Test
    void signupTotpVerifyRequiresSessionId() throws Exception {
        mockMvc.perform(post("/auth/signup/totp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"totpCode\":\"123456\"}"))
                .andExpect(status().isBadRequest());
    }
}
