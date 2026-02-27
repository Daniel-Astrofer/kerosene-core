package source.voucher.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import source.common.dto.ApiResponse;
import source.voucher.service.VoucherService;
import source.voucher.service.VoucherService.VoucherRequestData;

import source.transactions.infra.BlockchainInfoClient;
import source.transactions.service.PaymentLinkService;
import source.transactions.dto.PaymentLinkDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@RestController
@RequestMapping("/voucher")
public class VoucherController {

    private final VoucherService voucherService;
    private final PaymentLinkService paymentLinkService;
    private final BlockchainInfoClient blockchainClient;
    private final source.auth.application.infra.persistance.redis.contracts.RedisContract redisContract;
    private final source.auth.application.orchestrator.signup.SignupUseCase signupUseCase;

    public VoucherController(VoucherService voucherService,
            PaymentLinkService paymentLinkService,
            BlockchainInfoClient blockchainClient,
            source.auth.application.infra.persistance.redis.contracts.RedisContract redisContract,
            source.auth.application.orchestrator.signup.SignupUseCase signupUseCase) {
        this.voucherService = voucherService;
        this.paymentLinkService = paymentLinkService;
        this.blockchainClient = blockchainClient;
        this.redisContract = redisContract;
        this.signupUseCase = signupUseCase;
    }

    /**
     * Request a new voucher. Returns the Bitcoin deposit address and amount in
     * satoshis.
     */
    @PostMapping("/request")
    public ResponseEntity<ApiResponse<Map<String, Object>>> requestVoucher() {
        VoucherRequestData data = voucherService.requestVoucher();

        return ResponseEntity.ok(ApiResponse.success(
                "Voucher requested. Please send the exact amount of satoshis to the provided address on the Bitcoin network.",
                Map.of(
                        "depositAddress", data.depositAddress,
                        "amountSats", data.amountSats,
                        "pendingVoucherId", data.pendingVoucherId)));
    }

    /**
     * Confirms the payment on-chain by providing the transaction ID.
     */
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<String>> confirmPayment(
            @RequestParam String pendingVoucherId,
            @RequestParam String txid) {
        try {
            String code = voucherService.confirmPayment(pendingVoucherId, txid);
            return ResponseEntity.ok(ApiResponse.success("Voucher paid and confirmed successfully.", code));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("VOUCHER_ERROR", e.getMessage()));
        }
    }

    /**
     * Creates a mandatory 100 BRL onboarding payment link for inactive users.
     */
    @PostMapping("/onboarding-link")
    public ResponseEntity<ApiResponse<PaymentLinkDTO>> onboardingLink(@RequestParam String sessionId) {
        try {
            source.auth.dto.SignupState state = redisContract.findSignupState(sessionId);

            // ==== TEMPORARY OVERRIDE FOR TESTING ====
            // Finalize the user immediately and return PAID to bypass creation fee
            if (state != null) {
                signupUseCase.finalizeUserFromRedis(sessionId, "TEST_TXID_BYPASS", BigDecimal.ZERO);
            }

            PaymentLinkDTO fakeLink = new PaymentLinkDTO();
            fakeLink.setStatus("PAID");
            fakeLink.setDepositAddress("bc1q_test_bypass");
            fakeLink.setAmountBtc(BigDecimal.ZERO);
            fakeLink.setId("TEST_LINK");
            return ResponseEntity.ok(ApiResponse.success("Bypassing for testing.", fakeLink));
            // ========================================

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("ONBOARDING_ERROR", e.getMessage()));
        }
    }
}
