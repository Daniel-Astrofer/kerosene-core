package source.mining.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.common.dto.ApiResponse;
import source.mining.dto.MiningAllocationRequestDTO;
import source.mining.dto.MiningAllocationResponseDTO;
import source.mining.dto.MiningRigOfferDTO;
import source.mining.service.MiningService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/mining")
public class MiningController {

    private final MiningService miningService;

    public MiningController(MiningService miningService) {
        this.miningService = miningService;
    }

    @GetMapping("/rigs")
    public ResponseEntity<ApiResponse<List<MiningRigOfferDTO>>> listRigOffers() {
        List<MiningRigOfferDTO> response = miningService.listRigOffers();
        return ResponseEntity.ok(ApiResponse.success("Mining rig marketplace retrieved successfully.", response));
    }

    @PostMapping("/allocations")
    public ResponseEntity<ApiResponse<MiningAllocationResponseDTO>> createAllocation(
            @RequestBody MiningAllocationRequestDTO request,
            Authentication authentication)
    {
        MiningAllocationResponseDTO response = miningService.createAllocation(authenticatedUserId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Mining allocation created successfully.", response));
    }

    @GetMapping("/allocations")
    public ResponseEntity<ApiResponse<List<MiningAllocationResponseDTO>>> listAllocations(Authentication authentication)
    {
        List<MiningAllocationResponseDTO> response = miningService.listAllocations(authenticatedUserId(authentication));
        return ResponseEntity.ok(ApiResponse.success("Mining allocations retrieved successfully.", response));
    }

    @GetMapping("/allocations/{allocationId}")
    public ResponseEntity<ApiResponse<MiningAllocationResponseDTO>> getAllocation(
            @PathVariable UUID allocationId,
            Authentication authentication)
    {
        MiningAllocationResponseDTO response = miningService.getAllocation(authenticatedUserId(authentication), allocationId);
        return ResponseEntity.ok(ApiResponse.success("Mining allocation retrieved successfully.", response));
    }

    @PostMapping("/allocations/{allocationId}/cancel")
    public ResponseEntity<ApiResponse<MiningAllocationResponseDTO>> cancelAllocation(
            @PathVariable UUID allocationId,
            Authentication authentication)
    {
        MiningAllocationResponseDTO response = miningService.cancelAllocation(authenticatedUserId(authentication), allocationId);
        return ResponseEntity.ok(ApiResponse.success("Mining allocation cancelled successfully.", response));
    }

    private Long authenticatedUserId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
