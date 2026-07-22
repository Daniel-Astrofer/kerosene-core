package source.auth.controller;

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
import source.common.financial.FinancialColdWalletPsbtApprovalRequest;
import source.common.financial.FinancialCustodyTransferApprovalRequest;
import source.common.financial.FinancialLocalFactorApprovalRequest;
import source.common.financial.FinancialTransactionApprovalPort;
import source.common.financial.FinancialWalletOutboundApprovalRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/internal/kfe/transaction-approval")
public class KfeInternalTransactionApprovalController {

    private final FinancialTransactionApprovalPort approvalPort;
    private final String internalSecret;

    public KfeInternalTransactionApprovalController(
            FinancialTransactionApprovalPort approvalPort,
            @Value("${kfe.internal.shared-secret:}") String internalSecret) {
        this.approvalPort = approvalPort;
        this.internalSecret = internalSecret;
    }

    @PostMapping("/local-factor")
    public ResponseEntity<ApiResponse<Void>> approveLocalFactor(
            @RequestHeader(name = "X-KFE-Internal-Secret", required = false) String credential,
            @RequestBody FinancialLocalFactorApprovalRequest request) {
        verifyCredential(credential);
        require(request != null && request.userId() != null, "userId is required");
        approvalPort.approveLocalFactor(request.userId(), request.deviceRef(), request.factor());
        return ResponseEntity.ok(ApiResponse.success("KFE local factor approved.", null));
    }

    @PostMapping("/custody-transfer")
    public ResponseEntity<ApiResponse<Void>> approveCustodyTransfer(
            @RequestHeader(name = "X-KFE-Internal-Secret", required = false) String credential,
            @RequestBody FinancialCustodyTransferApprovalRequest request) {
        verifyCredential(credential);
        require(request != null && request.userId() != null, "userId is required");
        approvalPort.approveCustodyTransfer(request.userId(), request.assertion());
        return ResponseEntity.ok(ApiResponse.success("KFE custody transfer approved.", null));
    }

    @PostMapping("/wallet-outbound")
    public ResponseEntity<ApiResponse<Void>> approveWalletOutbound(
            @RequestHeader(name = "X-KFE-Internal-Secret", required = false) String credential,
            @RequestBody FinancialWalletOutboundApprovalRequest request) {
        verifyCredential(credential);
        require(request != null && request.actorUserId() != null, "actorUserId is required");
        require(request.ownerUserId() != null, "ownerUserId is required");
        approvalPort.approveWalletOutbound(
                request.actorUserId(),
                request.ownerUserId(),
                request.factorA(),
                request.factorB(),
                request.factorC());
        return ResponseEntity.ok(ApiResponse.success("KFE wallet outbound approved.", null));
    }

    @PostMapping("/cold-wallet-psbt")
    public ResponseEntity<ApiResponse<Void>> approveColdWalletPsbt(
            @RequestHeader(name = "X-KFE-Internal-Secret", required = false) String credential,
            @RequestBody FinancialColdWalletPsbtApprovalRequest request) {
        verifyCredential(credential);
        require(request != null && request.userId() != null, "userId is required");
        approvalPort.approveColdWalletPsbt(request.userId(), request.factor());
        return ResponseEntity.ok(ApiResponse.success("KFE cold wallet PSBT approved.", null));
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
