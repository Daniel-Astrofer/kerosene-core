package source.bitcoinaccounts.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.service.BitcoinAccountService;
import source.bitcoinaccounts.service.BitcoinTaxEventService;
import source.bitcoinaccounts.service.PsbtWorkflowService;
import source.bitcoinaccounts.service.ReceivingRequestService;
import source.common.dto.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/bitcoin")
@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")
public class BitcoinAccountsController {

    private final BitcoinAccountService accountService;
    private final ReceivingRequestService receivingRequestService;
    private final PsbtWorkflowService psbtWorkflowService;
    private final BitcoinTaxEventService taxEventService;

    public BitcoinAccountsController(
            BitcoinAccountService accountService,
            ReceivingRequestService receivingRequestService,
            PsbtWorkflowService psbtWorkflowService,
            BitcoinTaxEventService taxEventService) {
        this.accountService = accountService;
        this.receivingRequestService = receivingRequestService;
        this.psbtWorkflowService = psbtWorkflowService;
        this.taxEventService = taxEventService;
    }

    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Bitcoin accounts retrieved.",
                accountService.list(userId(authentication))));
    }

    @PostMapping("/accounts/internal-card")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createInternalCard(
            Authentication authentication,
            @RequestBody CreateInternalCardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Internal BTC Card created.",
                accountService.createInternalCard(userId(authentication), request.label(), request.riskTier())));
    }

    @PostMapping("/accounts/cold-wallet")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createColdWallet(
            Authentication authentication,
            @Valid @RequestBody CreateColdWalletRequest request) {
        BitcoinAccountEnums.ScriptPolicy scriptPolicy = parseScriptPolicy(request.scriptPolicy());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Watch-only cold wallet imported. Kerosene cannot sign for this wallet.",
                accountService.createColdWallet(
                        userId(authentication),
                        request.label(),
                        request.descriptor(),
                        request.xpub(),
                        request.fingerprint(),
                        request.derivationPath(),
                        scriptPolicy)));
    }

    @PostMapping("/accounts/{accountId}/receive-requests")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createReceiveRequest(
            Authentication authentication,
            @PathVariable UUID accountId,
            @RequestBody CreateReceiveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Receive link created.",
                receivingRequestService.create(
                        userId(authentication),
                        accountId,
                        request.amountSats(),
                        request.expiry(),
                        request.oneTime() == null || request.oneTime())));
    }

    @GetMapping("/accounts/{accountId}/receive-requests")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listReceiveRequests(
            Authentication authentication,
            @PathVariable UUID accountId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Receive requests retrieved.",
                receivingRequestService.listForAccount(userId(authentication), accountId)));
    }

    @GetMapping("/receive/{publicCode}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publicReceive(@PathVariable String publicCode) {
        return ResponseEntity.ok(ApiResponse.success(
                "Receive link retrieved.",
                receivingRequestService.publicView(publicCode)));
    }

    @GetMapping("/receive-requests/{id}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> receiveStatus(
            Authentication authentication,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Receive status retrieved.",
                receivingRequestService.ownerStatus(userId(authentication), id)));
    }

    @PostMapping("/receive-requests/{id}/hide")
    public ResponseEntity<ApiResponse<Map<String, Object>>> hideReceiveRequest(
            Authentication authentication,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Receive link hidden from your active list.",
                receivingRequestService.hide(userId(authentication), id)));
    }

    @PostMapping("/receive-requests/{id}/expire")
    public ResponseEntity<ApiResponse<Map<String, Object>>> expireReceiveRequest(
            Authentication authentication,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Receive link expired for new automatic payments.",
                receivingRequestService.expire(userId(authentication), id)));
    }

    @PostMapping("/receive-requests/{id}/user-action")
    public ResponseEntity<ApiResponse<Map<String, Object>>> receiveUserAction(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody UserActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Self-service action accepted.",
                receivingRequestService.userAction(userId(authentication), id, request.action())));
    }

    @PostMapping("/cold-wallets/{coldWalletId}/psbt")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createPsbt(
            Authentication authentication,
            @PathVariable UUID coldWalletId,
            @Valid @RequestBody CreatePsbtRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Unsigned PSBT created. Sign it in your external wallet.",
                psbtWorkflowService.createUnsigned(
                        userId(authentication),
                        coldWalletId,
                        request.destinationAddress(),
                        request.amountSats(),
                        request.feeRate() != null ? request.feeRate() : 0L,
                        request.selectedUtxoIds())));
    }

    @GetMapping("/cold-wallets/{coldWalletId}/utxos")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listColdWalletUtxos(
            Authentication authentication,
            @PathVariable UUID coldWalletId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Watch-only UTXOs retrieved.",
                psbtWorkflowService.listUtxos(userId(authentication), coldWalletId)));
    }

    @GetMapping("/cold-wallets/{coldWalletId}/psbt")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listColdWalletPsbt(
            Authentication authentication,
            @PathVariable UUID coldWalletId) {
        return ResponseEntity.ok(ApiResponse.success(
                "PSBT workflows retrieved.",
                psbtWorkflowService.listForColdWallet(userId(authentication), coldWalletId)));
    }

    @PostMapping("/psbt/{workflowId}/signed")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitSignedPsbt(
            Authentication authentication,
            @PathVariable UUID workflowId,
            @Valid @RequestBody SubmitSignedPsbtRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                request.broadcast() ? "PSBT validated and broadcasted." : "PSBT validated.",
                psbtWorkflowService.submitSigned(
                        userId(authentication),
                        workflowId,
                        request.signedPsbt(),
                        request.broadcast())));
    }

    @GetMapping("/psbt/{workflowId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPsbt(
            Authentication authentication,
            @PathVariable UUID workflowId) {
        return ResponseEntity.ok(ApiResponse.success(
                "PSBT workflow retrieved.",
                psbtWorkflowService.get(userId(authentication), workflowId)));
    }

    @GetMapping("/tax-events")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listTaxEvents(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Temporary tax events retrieved.",
                taxEventService.listTemporaryEvents(userId(authentication))));
    }

    @GetMapping("/tax-events/export")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exportTaxEvents(
            Authentication authentication,
            @RequestParam(defaultValue = "json") String format) {
        return ResponseEntity.ok(ApiResponse.success(
                "Temporary tax events exported.",
                taxEventService.export(userId(authentication), format)));
    }

    @PostMapping("/tax-events/{eventId}/classify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> classifyTaxEvent(
            Authentication authentication,
            @PathVariable UUID eventId,
            @Valid @RequestBody ClassifyTaxEventRequest request) {
        taxEventService.classify(userId(authentication), eventId, request.classification());
        return ResponseEntity.ok(ApiResponse.success(
                "Tax event classification updated.",
                Map.of("id", eventId, "classification", request.classification())));
    }

    private Long userId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Authentication is required.");
        }
        return Long.parseLong(authentication.getName());
    }

    private BitcoinAccountEnums.ScriptPolicy parseScriptPolicy(String raw) {
        if (raw == null || raw.isBlank()) {
            return BitcoinAccountEnums.ScriptPolicy.SINGLE_SIG;
        }
        try {
            return BitcoinAccountEnums.ScriptPolicy.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("scriptPolicy must be SINGLE_SIG or MULTISIG.");
        }
    }

    public record CreateInternalCardRequest(String label, String riskTier) {
    }

    public record CreateColdWalletRequest(
            String label,
            String descriptor,
            String xpub,
            @NotBlank(message = "fingerprint is required") String fingerprint,
            @NotBlank(message = "derivationPath is required") String derivationPath,
            String scriptPolicy) {
    }

    public record CreateReceiveRequest(
            @Positive(message = "amountSats must be positive when provided") Long amountSats,
            String expiry,
            Boolean oneTime) {
    }

    public record UserActionRequest(@NotBlank(message = "action is required") String action) {
    }

    public record CreatePsbtRequest(
            @NotBlank(message = "destinationAddress is required") String destinationAddress,
            @Positive(message = "amountSats must be positive") long amountSats,
            Long feeRate,
            List<UUID> selectedUtxoIds) {
    }

    public record SubmitSignedPsbtRequest(
            @NotBlank(message = "signedPsbt is required") String signedPsbt,
            @NotNull(message = "broadcast is required") Boolean broadcast) {
    }

    public record ClassifyTaxEventRequest(
            @NotBlank(message = "classification is required") String classification) {
    }
}
