package com.kerosene.common.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.kerosene.common.financial.StompUserPublishRequest;
import com.kerosene.common.service.StompUserRelayService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class KfeInternalStompRelayControllerTest {

    private final StompUserRelayService relayService = mock(StompUserRelayService.class);
    private final KfeInternalStompRelayController controller =
            new KfeInternalStompRelayController(relayService, "credential");

    @Test
    void forwardsAllowlistedPublishWhenCredentialMatches() {
        controller.publish(
                "credential",
                new StompUserPublishRequest(42L, "/queue/transactions", Map.of("id", "tx-1")));

        verify(relayService).publishToUser(42L, "/queue/transactions", Map.of("id", "tx-1"));
    }

    @Test
    void rejectsInvalidCredential() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.publish(
                        "wrong",
                        new StompUserPublishRequest(42L, "/queue/balance", Map.of("walletId", "w"))));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(relayService);
    }

    @Test
    void rejectsBlankDestination() {
        assertThrows(
                IllegalArgumentException.class,
                () -> controller.publish(
                        "credential",
                        new StompUserPublishRequest(42L, "  ", Map.of("id", "1"))));
        verifyNoInteractions(relayService);
    }
}
