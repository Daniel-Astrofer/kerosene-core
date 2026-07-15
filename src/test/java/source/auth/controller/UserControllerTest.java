package source.auth.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import source.auth.application.orchestrator.login.contracts.Login;
import source.auth.application.orchestrator.login.contracts.Signup;
import source.auth.application.usecase.user.GeneratePowChallengeUseCase;
import source.auth.application.usecase.user.LogoutCurrentSessionUseCase;
import source.auth.dto.UserDTO;
import source.common.exception.GlobalExceptionHandler;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private final Login login = mock(Login.class);
    private final Signup signup = mock(Signup.class);
    private final GeneratePowChallengeUseCase generatePowChallengeUseCase = mock(GeneratePowChallengeUseCase.class);
    private final LogoutCurrentSessionUseCase logoutCurrentSessionUseCase = mock(LogoutCurrentSessionUseCase.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new UserController(login, signup, generatePowChallengeUseCase, logoutCurrentSessionUseCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void getPowChallengeReturnsUseCasePayloadWithExistingResponseContract() throws Exception {
        when(generatePowChallengeUseCase.execute()).thenReturn(Map.of("challenge", "challenge-1"));

        mockMvc.perform(get("/auth/pow/challenge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("PoW Challenge generated"))
                .andExpect(jsonPath("$.data.challenge").value("challenge-1"));

        verify(generatePowChallengeUseCase).execute();
    }

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

    @Test
    void logoutRevokesCurrentBearerToken() throws Exception {
        when(logoutCurrentSessionUseCase.execute("Bearer token-1"))
                .thenReturn(new LogoutCurrentSessionUseCase.Result(LogoutCurrentSessionUseCase.Status.REVOKED));

        mockMvc.perform(post("/auth/logout")
                .header("Authorization", "Bearer token-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Session revoked."));

        verify(logoutCurrentSessionUseCase).execute("Bearer token-1");
    }

    @Test
    void logoutRejectsMissingBearerToken() throws Exception {
        when(logoutCurrentSessionUseCase.execute(null))
                .thenReturn(new LogoutCurrentSessionUseCase.Result(LogoutCurrentSessionUseCase.Status.MISSING_TOKEN));

        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication is required to logout."))
                .andExpect(jsonPath("$.errorCode").value("AUTH_013"));

        verify(logoutCurrentSessionUseCase).execute(null);
    }

    @Test
    void logoutDoesNotExposeRawRevocationErrors() throws Exception {
        when(logoutCurrentSessionUseCase.execute("Bearer bad-token"))
                .thenReturn(new LogoutCurrentSessionUseCase.Result(LogoutCurrentSessionUseCase.Status.REVOCATION_FAILED));

        mockMvc.perform(post("/auth/logout")
                .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unable to revoke the current session."))
                .andExpect(jsonPath("$.errorCode").value("AUTH_013"));

        verify(logoutCurrentSessionUseCase).execute("Bearer bad-token");
    }
}
