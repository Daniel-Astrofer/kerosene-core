package source.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import source.auth.AuthExceptions;
import source.auth.application.orchestrator.recovery.EmergencyRecoveryUseCase;
import source.auth.dto.EmergencyRecoveryFinishRequest;
import source.auth.dto.EmergencyRecoveryStartRequest;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmergencyRecoveryControllerTest {

    private final EmergencyRecoveryUseCase emergencyRecoveryUseCase = mock(EmergencyRecoveryUseCase.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new EmergencyRecoveryController(emergencyRecoveryUseCase))
            .build();

    @Test
    void startDoesNotExposeRejectedRecoveryReason() throws Exception {
        when(emergencyRecoveryUseCase.start(any(EmergencyRecoveryStartRequest.class), anyString()))
                .thenThrow(new AuthExceptions.RecoveryRejectedException(
                        "raw SQL recovery code mismatch for username alice"));

        mockMvc.perform(post("/auth/recovery/emergency/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Emergency recovery request was rejected."))
                .andExpect(jsonPath("$.message").value(not("raw SQL recovery code mismatch for username alice")))
                .andExpect(jsonPath("$.errorCode").value("RECOVERY_REJECTED"));
    }

    @Test
    void finishDoesNotExposeExpiredSessionReason() throws Exception {
        when(emergencyRecoveryUseCase.finish(any(EmergencyRecoveryFinishRequest.class)))
                .thenThrow(new AuthExceptions.RecoverySessionExpiredException(
                        "redis getdel failed for recovery:session-1"));

        mockMvc.perform(post("/auth/recovery/emergency/finish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Emergency recovery session is expired or already consumed."))
                .andExpect(jsonPath("$.message").value(not("redis getdel failed for recovery:session-1")))
                .andExpect(jsonPath("$.errorCode").value("RECOVERY_SESSION_EXPIRED"));
    }
}
