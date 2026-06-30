package source.notification.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import source.common.dto.ApiResponse;
import source.common.financial.FinancialDemoBalanceCreditedNotificationRequest;
import source.common.financial.FinancialDepositConfirmedNotificationRequest;
import source.common.financial.FinancialNotificationPort;
import source.common.financial.FinancialPaymentRequestDepositConfirmedNotificationRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/internal/kfe/notifications")
public class KfeInternalFinancialNotificationController {

    private final FinancialNotificationPort notificationPort;
    private final String internalSecret;

    public KfeInternalFinancialNotificationController(
            FinancialNotificationPort notificationPort,
            @Value("${kfe.internal.shared-secret:}") String internalSecret) {
        this.notificationPort = notificationPort;
        this.internalSecret = internalSecret;
    }

    @PostMapping("/deposit-confirmed")
    public ResponseEntity<ApiResponse<Void>> notifyDepositConfirmed(
            @RequestHeader(name = "X-KFE-Internal-Secret", required = false) String credential,
            @RequestBody FinancialDepositConfirmedNotificationRequest request) {
        verifyCredential(credential);
        require(request != null, "request is required");
        require(request.userId() != null, "userId is required");
        require(request.transactionId() != null, "transactionId is required");
        require(request.walletId() != null, "walletId is required");

        notificationPort.notifyDepositConfirmed(
                request.userId(),
                request.transactionId(),
                request.walletId(),
                request.rail(),
                request.creditedSats(),
                request.confirmations());
        return ResponseEntity.ok(ApiResponse.success("KFE deposit notification accepted.", null));
    }

    @PostMapping("/payment-request-deposit-confirmed")
    public ResponseEntity<ApiResponse<Void>> notifyPaymentRequestDepositConfirmed(
            @RequestHeader(name = "X-KFE-Internal-Secret", required = false) String credential,
            @RequestBody FinancialPaymentRequestDepositConfirmedNotificationRequest request) {
        verifyCredential(credential);
        require(request != null, "request is required");
        require(request.userId() != null, "userId is required");
        require(request.transactionId() != null, "transactionId is required");
        require(request.paymentRequestId() != null, "paymentRequestId is required");
        require(request.walletId() != null, "walletId is required");

        notificationPort.notifyPaymentRequestDepositConfirmed(
                request.userId(),
                request.transactionId(),
                request.paymentRequestId(),
                request.publicId(),
                request.walletId(),
                request.rail(),
                request.creditedSats());
        return ResponseEntity.ok(ApiResponse.success("KFE payment request notification accepted.", null));
    }

    @PostMapping("/demo-balance-credited")
    public ResponseEntity<ApiResponse<Void>> notifyDemoBalanceCredited(
            @RequestHeader(name = "X-KFE-Internal-Secret", required = false) String credential,
            @RequestBody FinancialDemoBalanceCreditedNotificationRequest request) {
        verifyCredential(credential);
        require(request != null, "request is required");
        require(request.userId() != null, "userId is required");
        require(request.walletId() != null, "walletId is required");

        notificationPort.notifyDemoBalanceCredited(
                request.userId(),
                request.walletId(),
                request.walletName(),
                request.amountBtc());
        return ResponseEntity.ok(ApiResponse.success("KFE demo balance notification accepted.", null));
    }

    private void verifyCredential(String credential) {
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "KFE internal shared secret is not configured");
        }
        if (credential == null || credential.isBlank() || !constantTimeEquals(internalSecret, credential)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid KFE internal credential");
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
