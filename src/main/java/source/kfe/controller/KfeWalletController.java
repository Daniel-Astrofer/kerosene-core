package source.kfe.controller;

import jakarta.validation.Valid;
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
import source.kfe.dto.KfeAddressResponse;
import source.kfe.dto.KfeCreateWalletRequest;
import source.kfe.dto.KfeWalletResponse;
import source.kfe.service.KfeWalletService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/kfe/wallets")
public class KfeWalletController {

    private final KfeWalletService walletService;

    public KfeWalletController(KfeWalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<KfeWalletResponse>> create(
            @Valid @RequestBody KfeCreateWalletRequest request,
            Authentication authentication) {
        KfeWalletResponse response = walletService.createWallet(authenticatedUserId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("KFE wallet created.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<KfeWalletResponse>>> list(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "KFE wallets retrieved.",
                walletService.listWallets(authenticatedUserId(authentication))));
    }

    @PostMapping("/{walletId}/addresses/rotate")
    public ResponseEntity<ApiResponse<KfeAddressResponse>> rotateAddress(
            @PathVariable UUID walletId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "KFE wallet address rotated.",
                walletService.rotateAddress(authenticatedUserId(authentication), walletId)));
    }

    private Long authenticatedUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new SecurityException("Authenticated user is required.");
        }
        return Long.parseLong(authentication.getName());
    }
}
