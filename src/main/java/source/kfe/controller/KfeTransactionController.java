package source.kfe.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import source.common.dto.ApiResponse;
import source.kfe.dto.KfeSubmitTransactionRequest;
import source.kfe.dto.KfeTransactionResponse;
import source.kfe.repository.KfeTransactionRepository;
import source.kfe.service.KfeResponseMapper;
import source.kfe.service.KfeTransactionEngine;

import java.util.UUID;

@RestController
@RequestMapping("/kfe/transactions")
public class KfeTransactionController {

    private final KfeTransactionEngine transactionEngine;
    private final KfeTransactionRepository transactionRepository;
    private final KfeResponseMapper responseMapper;

    public KfeTransactionController(
            KfeTransactionEngine transactionEngine,
            KfeTransactionRepository transactionRepository,
            KfeResponseMapper responseMapper) {
        this.transactionEngine = transactionEngine;
        this.transactionRepository = transactionRepository;
        this.responseMapper = responseMapper;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<KfeTransactionResponse>> submit(
            @Valid @RequestBody KfeSubmitTransactionRequest request,
            Authentication authentication) {
        KfeTransactionResponse response = transactionEngine.submit(authenticatedUserId(authentication), request);
        return ResponseEntity.ok(ApiResponse.success("KFE transaction accepted.", response));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<KfeTransactionResponse>> get(
            @PathVariable UUID transactionId,
            Authentication authentication) {
        KfeTransactionResponse response = transactionRepository
                .findByIdAndUserId(transactionId, authenticatedUserId(authentication))
                .map(responseMapper::toTransactionResponse)
                .orElseThrow(() -> new IllegalArgumentException("KFE transaction not found."));
        return ResponseEntity.ok(ApiResponse.success("KFE transaction retrieved.", response));
    }

    private Long authenticatedUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new SecurityException("Authenticated user is required.");
        }
        return Long.parseLong(authentication.getName());
    }
}
