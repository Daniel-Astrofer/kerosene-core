package com.kerosene.auth.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.kerosene.auth.AuthConstants;
import com.kerosene.common.dto.ApiResponse;
import com.kerosene.common.exception.ErrorCodes;
import com.kerosene.common.financial.FinancialUserDirectoryLookupRequest;
import com.kerosene.common.financial.FinancialUserDirectoryPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

@RestController
@RequestMapping("/internal/kfe/user-directory")
public class KfeInternalUserDirectoryController {

    private final FinancialUserDirectoryPort userDirectory;
    private final String internalSecret;

    public KfeInternalUserDirectoryController(
            FinancialUserDirectoryPort userDirectory,
            @Value("${kfe.internal.shared-secret:}") String internalSecret) {
        this.userDirectory = userDirectory;
        this.internalSecret = internalSecret;
    }

    @PostMapping("/lookup")
    public ResponseEntity<ApiResponse<FinancialUserDirectoryPort.FinancialUserHandle>> lookup(
            @RequestHeader(name = "X-KFE-Internal-Secret", required = false) String credential,
            @RequestBody FinancialUserDirectoryLookupRequest request) {
        verifyCredential(credential);
        validateRequest(request);

        Optional<FinancialUserDirectoryPort.FinancialUserHandle> user = hasText(request.username())
                ? userDirectory.findByUsername(request.username())
                : userDirectory.findById(request.userId());
        if (user.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found.", ErrorCodes.AUTH_USER_NOT_FOUND));
        }
        return ResponseEntity.ok(ApiResponse.success("User resolved.", user.get()));
    }

    private void validateRequest(FinancialUserDirectoryLookupRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lookup request is required");
        }
        boolean hasUsername = hasText(request.username());
        boolean hasUserId = request.userId() != null;
        if (hasUsername == hasUserId) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "exactly one of username or userId is required");
        }
        if (hasUsername && request.username().trim().length() > AuthConstants.USERNAME_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username is too long");
        }
        if (hasUserId && request.userId() <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId must be positive");
        }
    }

    private void verifyCredential(String credential) {
        if (!hasText(internalSecret)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "KFE internal shared secret is not configured");
        }
        if (!hasText(credential) || !constantTimeEquals(internalSecret, credential)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid KFE internal credential");
        }
    }

    private boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
