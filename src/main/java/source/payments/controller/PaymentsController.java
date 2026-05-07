package source.payments.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import source.common.dto.ApiResponse;
import source.payments.dto.PaymentConfirmRequest;
import source.payments.dto.PaymentQuoteRequest;
import source.payments.dto.PaymentQuoteResponse;
import source.payments.dto.PaymentStatusResponse;
import source.payments.dto.ReceivingCapabilitiesResponse;
import source.payments.exception.PaymentException;
import source.payments.service.PaymentConfirmService;
import source.payments.service.PaymentQuoteService;
import source.payments.service.ReceivingCapabilityService;

import java.util.UUID;

@RestController
public class PaymentsController {

    private final PaymentQuoteService paymentQuoteService;
    private final PaymentConfirmService paymentConfirmService;
    private final ReceivingCapabilityService receivingCapabilityService;

    public PaymentsController(
            PaymentQuoteService paymentQuoteService,
            PaymentConfirmService paymentConfirmService,
            ReceivingCapabilityService receivingCapabilityService) {
        this.paymentQuoteService = paymentQuoteService;
        this.paymentConfirmService = paymentConfirmService;
        this.receivingCapabilityService = receivingCapabilityService;
    }

    @PostMapping("/payments/quote")
    public ResponseEntity<ApiResponse<PaymentQuoteResponse>> quote(
            Authentication authentication,
            @Valid @RequestBody PaymentQuoteRequest request) {
        PaymentQuoteResponse response = paymentQuoteService.quote(authenticatedUserId(authentication), request);
        return ResponseEntity.ok(ApiResponse.success("Cotação gerada com segurança.", response));
    }

    @PostMapping("/payments/{paymentIntentId}/confirm")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> confirm(
            Authentication authentication,
            @PathVariable UUID paymentIntentId,
            @Valid @RequestBody PaymentConfirmRequest request) {
        PaymentStatusResponse response = paymentConfirmService.confirm(
                authenticatedUserId(authentication),
                paymentIntentId,
                request);
        return ResponseEntity.ok(ApiResponse.success("Envio confirmado.", response));
    }

    @GetMapping("/payments/{paymentIntentId}")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> status(
            Authentication authentication,
            @PathVariable UUID paymentIntentId) {
        PaymentStatusResponse response = paymentConfirmService.status(authenticatedUserId(authentication), paymentIntentId);
        return ResponseEntity.ok(ApiResponse.success("Status do envio atualizado.", response));
    }

    @GetMapping("/users/{receiverIdentifier}/receiving-capabilities")
    public ResponseEntity<ApiResponse<ReceivingCapabilitiesResponse>> receivingCapabilities(
            @PathVariable String receiverIdentifier) {
        ReceivingCapabilitiesResponse response = receivingCapabilityService.capabilities(receiverIdentifier);
        return ResponseEntity.ok(ApiResponse.success("Capacidades de recebimento consultadas.", response));
    }

    private Long authenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw PaymentException.badRequest(
                    "PAYMENT_AUTH_REQUIRED",
                    "Entre na sua conta para continuar.");
        }
        return Long.parseLong(authentication.getName());
    }
}
