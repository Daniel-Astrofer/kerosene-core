package com.kerosene.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kerosene.common.financial.approval.FinancialPaymentApprovalPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static com.kerosene.common.financial.approval.FinancialPaymentApprovalV1.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KfeInternalPaymentApprovalV1ControllerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final FinancialPaymentApprovalPort approvals = mock(FinancialPaymentApprovalPort.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(new KfeInternalPaymentApprovalV1Controller(approvals, "test-secret", json))
            .setControllerAdvice(new CatchAllAdvice()).build();
    private Request request() { return new Request(1, new Context(7, "device", "key", "INTERNAL", "INTERNAL", "source", "dest", 1000, 0, null, null, null, null, null, null), "1234", null, null, null); }

    @Test void validWireReachesOnlyNewPortAndReturnsTypedResponse() throws Exception {
        var request = request(); var hash = bindingHash(request.context());
        var c = new Challenge(1, PURPOSE, "id", "a".repeat(64), hash, "alice", "service", 100, 190, "Ed25519", CANONICALIZATION);
        when(approvals.approve(request)).thenReturn(new Response(1, "CHALLENGE", hash, null, 190, c));
        mvc.perform(post(PATH).header("X-KFE-Internal-Secret", "test-secret").contentType("application/json").content(json.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("CHALLENGE")).andExpect(jsonPath("challenge.bindingHash").value(hash));
        verify(approvals).approve(request);
    }

    @ParameterizedTest @ValueSource(strings = {"unknown", "version", "duplicate", "null", "proof", "size", "missing", "nullNumber", "stringNumber", "trailing"})
    void malformedRequestsCannotReachApproval(String kind) throws Exception {
        String body = json.writeValueAsString(request());
        body = switch (kind) {
            case "unknown" -> body.replaceFirst("\\{", "{\"extra\":true,");
            case "version" -> body.replace("\"version\":1", "\"version\":2");
            case "duplicate" -> body.replace("\"version\":1", "\"version\":1,\"version\":1");
            case "null" -> "null";
            case "proof" -> body.replace("\"proof\":null", "\"proof\":{\"version\":1,\"type\":\"DEVICE_KEY\"}");
            case "missing" -> body.replace("\"networkFeeSats\":0,", "");
            case "nullNumber" -> body.replace("\"networkFeeSats\":0", "\"networkFeeSats\":null");
            case "stringNumber" -> body.replace("\"networkFeeSats\":0", "\"networkFeeSats\":\"0\"");
            case "trailing" -> body + "{}";
            default -> " ".repeat(32769);
        };
        mvc.perform(post(PATH).header("X-KFE-Internal-Secret", "test-secret").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(approvals);
    }

    @Test void internalCredentialIsRequiredEvenBeforeBodyParsing() throws Exception {
        mvc.perform(post(PATH).contentType("application/json").content("malformed")).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("X-KFE-Internal-Secret", "wrong").contentType("application/json").content("null")).andExpect(status().isUnauthorized());
        var disabled = MockMvcBuilders.standaloneSetup(new KfeInternalPaymentApprovalV1Controller(approvals, "", json)).build();
        disabled.perform(post(PATH).contentType("application/json").content("null")).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(approvals);
    }

    @Test void rejectedProofKeepsStatusAndDoesNotExposeDiagnosticWithCatchAllAdvice() throws Exception {
        when(approvals.approve(any())).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "signature-secret"));
        mvc.perform(post(PATH).header("X-KFE-Internal-Secret", "test-secret").contentType("application/json").content(json.writeValueAsString(request())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("errorCode").value("KFE_PAYMENT_APPROVAL_REJECTED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("signature-secret"))));
    }

    @org.springframework.web.bind.annotation.RestControllerAdvice
    static class CatchAllAdvice {
        @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
        org.springframework.http.ResponseEntity<Void> unexpected(Exception failure) {
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }
}
