package com.kerosene.auth.controller;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kerosene.common.financial.approval.FinancialPaymentApprovalPort;
import com.kerosene.common.financial.approval.FinancialPaymentApprovalV1;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
public class KfeInternalPaymentApprovalV1Controller {
    private final FinancialPaymentApprovalPort approvals;
    private final String secret;
    private final ObjectMapper json;
    public KfeInternalPaymentApprovalV1Controller(FinancialPaymentApprovalPort approvals,
            @Value("${kfe.internal.shared-secret:}") String secret, ObjectMapper mapper) {
        this.approvals = approvals; this.secret = secret;
        this.json = mapper.copy().setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS);
    }

    @PostMapping(value = FinancialPaymentApprovalV1.PATH, consumes = "application/json", produces = "application/json")
    public FinancialPaymentApprovalV1.Response approve(
            @RequestHeader(name = "X-KFE-Internal-Secret", required = false) String credential, @RequestBody String body) {
        if (secret == null || secret.isBlank()) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Internal approval unavailable"); }
        if (credential == null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), credential.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal credential");
        }
        final FinancialPaymentApprovalV1.Request request;
        try {
            if (body == null || body.length() > 32_768) { throw new IllegalArgumentException(); }
            var tree = json.readTree(body);
            if (tree == null || !tree.isObject() || !tree.path("context").isObject()) { throw new IllegalArgumentException(); }
            for (var field : FinancialPaymentApprovalV1.Context.class.getRecordComponents()) {
                if (!tree.path("context").has(field.getName())) { throw new IllegalArgumentException(); }
            }
            request = json.treeToValue(tree, FinancialPaymentApprovalV1.Request.class);
            if (request == null) { throw new IllegalArgumentException(); }
        } catch (Exception failure) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment approval request"); }
        return approvals.approve(request);
    }

    // Keep protocol statuses even when the application's catch-all advice is installed.
    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<com.kerosene.common.dto.ApiResponse<Void>> rejected(ResponseStatusException failure) {
        return org.springframework.http.ResponseEntity.status(failure.getStatusCode()).body(
                com.kerosene.common.dto.ApiResponse.error("Payment approval rejected.", "KFE_PAYMENT_APPROVAL_REJECTED", null));
    }
}
