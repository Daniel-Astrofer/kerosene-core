package source.transactions.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import source.common.dto.ApiResponse;
import source.transactions.service.OnrampService;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Controller for third-party Onramp services (MoonPay, Banxa, Bipa).
 * Provides parameterized URLs with the user's dynamic wallet address injected.
 */
@RestController
@RequestMapping("/api/onramp")
public class OnrampController {

    private final OnrampService onrampService;

    public OnrampController(OnrampService onrampService) {
        this.onrampService = onrampService;
    }

    /**
     * Generates purchase URLs for all supported onramp providers.
     * The user's primary wallet address is automatically injected into the links.
     *
     * @param auth The authenticated user
     * @return Map of provider name to parameterized URL
     */
    @GetMapping("/urls")
    public ResponseEntity<ApiResponse<Map<String, String>>> getOnrampUrls(
            Authentication auth,
            @RequestParam(required = false) String walletName,
            @RequestParam(required = false) BigDecimal amountBtc) {
        try {
            Long userId = Long.parseLong(auth.getName());
            Map<String, String> urls = onrampService.generateOnrampUrls(userId, walletName, amountBtc);
            return ResponseEntity.ok(ApiResponse.success(
                    "Onramp provider URLs generated successfully with a dedicated monitored deposit address.",
                    urls));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage(), "ONRAMP_ERROR"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to generate onramp URLs.", "SERVER_ERROR"));
        }
    }
}
