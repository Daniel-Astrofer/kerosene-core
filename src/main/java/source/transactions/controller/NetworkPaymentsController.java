package source.transactions.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import source.common.dto.ApiResponse;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.dto.LightningInvoiceRequestDTO;
import source.transactions.dto.LightningInvoiceResponseDTO;
import source.transactions.dto.LightningPaymentRequestDTO;
import source.transactions.dto.OnchainAddressAllocationDTO;
import source.transactions.dto.OnchainAddressRequestDTO;
import source.transactions.dto.OnchainSendRequestDTO;
import source.transactions.dto.WalletNetworkAddressDTO;
import source.transactions.service.ExternalPaymentsService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transactions/network")
public class NetworkPaymentsController {

    private final ExternalPaymentsService externalPaymentsService;

    public NetworkPaymentsController(ExternalPaymentsService externalPaymentsService) {
        this.externalPaymentsService = externalPaymentsService;
    }

    @PostMapping("/onchain/address")
    public ResponseEntity<ApiResponse<OnchainAddressAllocationDTO>> issueOnchainAddress(
            @RequestBody OnchainAddressRequestDTO request,
            Authentication authentication) {
        OnchainAddressAllocationDTO response = externalPaymentsService.issueOnchainAddress(
                authenticatedUserId(authentication),
                request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("On-chain address issued successfully for the selected wallet.", response));
    }

    @GetMapping("/wallet-profile")
    public ResponseEntity<ApiResponse<WalletNetworkAddressDTO>> getWalletNetworkProfile(
            @RequestParam String walletName,
            Authentication authentication) {
        WalletNetworkAddressDTO response = externalPaymentsService.getWalletNetworkProfile(
                authenticatedUserId(authentication),
                walletName);
        return ResponseEntity.ok(ApiResponse.success("Wallet network profile retrieved successfully.", response));
    }

    @PostMapping("/onchain/send")
    public ResponseEntity<ApiResponse<ExternalTransferResponseDTO>> sendOnchain(
            @Valid @RequestBody OnchainSendRequestDTO request,
            Authentication authentication) {
        ExternalTransferResponseDTO response = externalPaymentsService.sendOnchain(
                authenticatedUserId(authentication),
                request);
        return ResponseEntity.ok(ApiResponse.success(
                "External on-chain payment has been queued and the fee for your wallet card profile was applied.",
                response));
    }

    @PostMapping("/lightning/invoice")
    public ResponseEntity<ApiResponse<LightningInvoiceResponseDTO>> createLightningInvoice(
            @Valid @RequestBody LightningInvoiceRequestDTO request,
            Authentication authentication) {
        LightningInvoiceResponseDTO response = externalPaymentsService.createLightningInvoice(
                authenticatedUserId(authentication),
                request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Lightning invoice created successfully.", response));
    }

    @PostMapping("/transfers/{transferId}/cancel")
    public ResponseEntity<ApiResponse<ExternalTransferResponseDTO>> cancelInboundTransfer(
            @PathVariable UUID transferId,
            Authentication authentication) {
        ExternalTransferResponseDTO response = externalPaymentsService.cancelInboundTransfer(
                authenticatedUserId(authentication),
                transferId);
        return ResponseEntity.ok(ApiResponse.success("Inbound transfer cancelled successfully.", response));
    }

    @PostMapping("/lightning/pay")
    public ResponseEntity<ApiResponse<ExternalTransferResponseDTO>> payLightning(
            @Valid @RequestBody LightningPaymentRequestDTO request,
            Authentication authentication) {
        ExternalTransferResponseDTO response = externalPaymentsService.payLightning(
                authenticatedUserId(authentication),
                request);
        return ResponseEntity.ok(ApiResponse.success(
                "Lightning payment sent successfully and the fee for your wallet card profile was applied.",
                response));
    }

    @GetMapping("/transfers")
    public ResponseEntity<ApiResponse<List<ExternalTransferResponseDTO>>> listTransfers(Authentication authentication) {
        List<ExternalTransferResponseDTO> response = externalPaymentsService.listTransfers(
                authenticatedUserId(authentication));
        return ResponseEntity.ok(ApiResponse.success("External transfer history retrieved successfully.", response));
    }

    @GetMapping("/transfers/{transferId}")
    public ResponseEntity<ApiResponse<ExternalTransferResponseDTO>> getTransfer(
            @PathVariable UUID transferId,
            Authentication authentication) {
        ExternalTransferResponseDTO response = externalPaymentsService.getTransfer(
                authenticatedUserId(authentication),
                transferId);
        return ResponseEntity.ok(ApiResponse.success("External transfer retrieved successfully.", response));
    }

    private Long authenticatedUserId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
