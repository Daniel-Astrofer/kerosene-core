package source.transactions.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.common.dto.ApiResponse;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.service.ExternalPaymentsService;

import java.util.UUID;

@RestController
@RequestMapping("/deposit")
public class DepositController {

    private final ExternalPaymentsService externalPaymentsService;

    public DepositController(ExternalPaymentsService externalPaymentsService) {
        this.externalPaymentsService = externalPaymentsService;
    }

    @PostMapping("/{transferId}/cancel")
    public ResponseEntity<ApiResponse<ExternalTransferResponseDTO>> cancelDeposit(
            @PathVariable UUID transferId,
            Authentication authentication) {
        ExternalTransferResponseDTO response = externalPaymentsService.cancelInboundTransfer(
                Long.parseLong(authentication.getName()),
                transferId);
        return ResponseEntity.ok(ApiResponse.success("Deposit cancelled successfully.", response));
    }
}
