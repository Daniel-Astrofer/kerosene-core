package source.wallet.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import source.wallet.dto.WalletRequestDTO;
import source.wallet.dto.WalletUpdateDTO;
import source.wallet.orchestrator.WalletUseCase;
import source.common.dto.ApiResponse;

@RestController
@RequestMapping("/wallet")
public class WalletController {
    private final WalletUseCase wallet;

    public WalletController(WalletUseCase wallet) {
        this.wallet = wallet;
    }

    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<String>> create(@Valid @RequestBody WalletRequestDTO dto,
            HttpServletRequest request) {
        wallet.createWallet(dto, getAuthenticatedUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("Awesome! Your wallet was successfully created and is ready to store funds."));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<Object>> getAllWallets(HttpServletRequest request) {
        return ResponseEntity
                .ok(ApiResponse.success("Successfully retrieved all your wallets.",
                        wallet.getAllWallets(getAuthenticatedUserId())));
    }

    @GetMapping("/find")
    public ResponseEntity<ApiResponse<Object>> getWalletByName(@RequestParam String name,
            HttpServletRequest request) {
        return ResponseEntity
                .ok(ApiResponse.success("Wallet successfully located.",
                        wallet.getWalletByName(name, getAuthenticatedUserId())));
    }

    @PutMapping("/update")
    public ResponseEntity<ApiResponse<String>> updateWallet(@Valid @RequestBody WalletUpdateDTO dto,
            HttpServletRequest request) {
        wallet.updateWallet(dto, getAuthenticatedUserId());
        return ResponseEntity.ok(ApiResponse.success("Your wallet details have been successfully updated."));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse<String>> deleteWallets(@RequestBody WalletRequestDTO dto,
            HttpServletRequest request) {
        wallet.deleteWallet(dto, getAuthenticatedUserId());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("Wallet successfully permanently deleted."));
    }

}
