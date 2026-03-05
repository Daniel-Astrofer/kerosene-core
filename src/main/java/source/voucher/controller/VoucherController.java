package source.voucher.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import source.common.dto.ApiResponse;
import source.voucher.service.VoucherService;
import source.voucher.service.VoucherService.VoucherRequestData;

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
    private final source.auth.application.infra.persistance.redis.contracts.RedisContract redisContract;

    public VoucherController(VoucherService voucherService,
            PaymentLinkService paymentLinkService,
            source.auth.application.infra.persistance.redis.contracts.RedisContract redisContract) {
        this.voucherService = voucherService;
        this.paymentLinkService = paymentLinkService;
        this.redisContract = redisContract;
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
     * Creates a mandatory fixed BTC onboarding payment link for inactive users.
     */
    @PostMapping("/onboarding-link")
    public ResponseEntity<ApiResponse<PaymentLinkDTO>> onboardingLink(@RequestParam String sessionId) {
        try {
            source.auth.dto.SignupState state = redisContract.findSignupState(sessionId);

            if (state == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("ONBOARDING_ERROR", "Session expired or finalized."));
            }

            if (!state.isPasskeyRegistered() || state.getPasskeyCredentialJson() == null
                    || state.getPasskeyCredentialJson().isEmpty()) {
                throw new source.auth.AuthExceptions.MissingPasskey(
                        "A Passkey (Biometrics/Hardware Key) has become MANDATORY. You must register it before attempting to generate the onboarding deposit link.");
            }

            // In production, we generate a real payment link and do NOT bypass user
            // creation
            PaymentLinkDTO paymentLink = paymentLinkService.createOnboardingPaymentLink(
                    sessionId,
                    new BigDecimal("0.00022000"), // Equivalent to the fixed SATOSHI fee
                    "ONBOARDING_VOUCHER");

            return ResponseEntity.ok(ApiResponse.success(
                    "Deposit the exact amount of " + paymentLink.getAmountBtc()
                            + " BTC to the address. Your account will be activated after 3 network confirmations.",
                    paymentLink));
        } catch (source.auth.AuthExceptions.MissingPasskey e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("ONBOARDING_MISSING_PASSKEY", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("ONBOARDING_SERVER_ERROR", e.getMessage()));
        }
    }
}
