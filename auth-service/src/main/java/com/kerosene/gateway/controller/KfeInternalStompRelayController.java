package com.kerosene.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.kerosene.common.dto.ApiResponse;
import com.kerosene.common.financial.stomp.StompUserPublishRequest;
import com.kerosene.gateway.realtime.StompUserRelayService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * KFE standalone → Core STOMP bridge.
 *
 * <p>Auth: {@code X-KFE-Internal-Secret} (same as other {@code /internal/kfe/**} routes).
 */
@RestController
@RequestMapping("/internal/kfe/stomp")
public class KfeInternalStompRelayController {

    /** Validates destinations and dispatches messages to connected user sessions. */
    private final StompUserRelayService relayService;
    /** Shared secret required from the KFE internal caller. */
    private final String internalSecret;

    /** Creates the internal bridge endpoint with its relay and configured credential. */
    /** @param relayService allowlisted user messaging gateway @param internalSecret expected internal credential */
    public KfeInternalStompRelayController(
            StompUserRelayService relayService,
            @Value("${kfe.internal.shared-secret:}") String internalSecret) {
        this.relayService = relayService;
        this.internalSecret = internalSecret;
    }

    /**
     * Authenticates KFE, validates the publish envelope, and relays the payload to a user queue.
     * @param credential caller-supplied shared secret
     * @param request target account, destination, and payload
     * @return accepted recipient and normalized destination
     */
    @PostMapping("/publish")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publish(
            @RequestHeader(name = "X-KFE-Internal-Secret", required = false) String credential,
            @RequestBody StompUserPublishRequest request) {
        verifyCredential(credential);
        require(request != null, "request is required");
        require(request.userId() != null, "userId is required");
        require(request.destination() != null && !request.destination().isBlank(), "destination is required");
        require(request.payload() != null && !request.payload().isEmpty(), "payload is required");

        try {
            relayService.publishToUser(request.userId(), request.destination(), request.payload());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        return ResponseEntity.ok(ApiResponse.success(
                "STOMP user publish accepted.",
                Map.of(
                        "userId", request.userId(),
                        "destination", StompUserRelayService.normalizeDestination(request.destination()))));
    }

    /** Rejects missing configuration and compares the supplied internal secret in constant time. */
    private void verifyCredential(String credential) {
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Internal secret not configured");
        }
        if (credential == null || credential.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing internal credential");
        }
        if (!constantTimeEquals(internalSecret, credential)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal credential");
        }
    }

    /** Compares UTF-8 credential bytes without content-dependent early exit. */
    private static boolean constantTimeEquals(String expected, String provided) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    /** Raises a 400 response when a request invariant is false. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }
}
