package source.transactions.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import source.transactions.application.externalpayments.ExternalPaymentsCustodyPort;
import source.transactions.infra.BitcoinCoreRpcClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuorumPsbtSigningService {

    private final ObjectProvider<BitcoinCoreRpcClient> bitcoinCoreRpcClient;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final NetworkTransferEventService networkTransferEventService;
    private final int requiredSignatures;
    private final int fundingConfirmationTarget;
    private final List<String> signerUrls;
    private final List<String> signerApiKeys;

    public QuorumPsbtSigningService(
            ObjectProvider<BitcoinCoreRpcClient> bitcoinCoreRpcClient,
            @Qualifier("custodyRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper,
            NetworkTransferEventService networkTransferEventService,
            @Value("${quorum.psbt.required-signatures:2}") int requiredSignatures,
            @Value("${quorum.psbt.funding-confirmation-target:6}") int fundingConfirmationTarget,
            @Value("${quorum.psbt.signer-urls:}") String signerUrls,
            @Value("${quorum.psbt.signer-api-keys:}") String signerApiKeys) {
        this.bitcoinCoreRpcClient = bitcoinCoreRpcClient;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.networkTransferEventService = networkTransferEventService;
        this.requiredSignatures = Math.max(1, requiredSignatures);
        this.fundingConfirmationTarget = Math.max(1, fundingConfirmationTarget);
        this.signerUrls = splitCsv(signerUrls);
        this.signerApiKeys = splitCsv(signerApiKeys);
    }

    public OnchainExecution execute(ExternalPaymentsCustodyPort.OnchainPaymentCommand command) {
        BitcoinCoreRpcClient bitcoinCore = bitcoinCoreRpcClient.getIfAvailable();
        if (bitcoinCore == null) {
            throw new IllegalStateException("Bitcoin Core RPC is required for on-chain payments.");
        }
        if (signerUrls.isEmpty()) {
            throw new IllegalStateException("No quorum PSBT signer endpoints are configured.");
        }

        BitcoinCoreRpcClient.FundedPsbt fundedPsbt = bitcoinCore.createFundedPsbt(
                command.destinationAddress(),
                command.amountSats(),
                fundingConfirmationTarget);
        if (fundedPsbt.psbt() == null || fundedPsbt.psbt().isBlank()) {
            throw new IllegalStateException("Bitcoin Core did not return a PSBT.");
        }

        networkTransferEventService.info(
                command.userId(),
                "PSBT_CREATED",
                command.destinationAddress(),
                "walletId=" + command.walletId() + " | amountSats=" + command.amountSats());

        List<String> partialPsbts = new ArrayList<>();
        partialPsbts.add(fundedPsbt.psbt());
        List<String> acceptedSigners = new ArrayList<>();

        for (int index = 0; index < signerUrls.size(); index++) {
            if (acceptedSigners.size() >= requiredSignatures) {
                break;
            }
            String signerUrl = signerUrls.get(index);
            String apiKey = index < signerApiKeys.size() ? signerApiKeys.get(index) : null;
            try {
                String signedPsbt = requestSignature(signerUrl, apiKey, fundedPsbt.psbt(), command);
                if (signedPsbt != null && !signedPsbt.isBlank()) {
                    partialPsbts.add(signedPsbt);
                    acceptedSigners.add("signer-" + (index + 1));
                }
            } catch (Exception ex) {
                networkTransferEventService.warn(
                        command.userId(),
                        "PSBT_SIGNER_UNAVAILABLE",
                        signerUrl,
                        ex.getMessage());
            }
        }

        if (acceptedSigners.size() < requiredSignatures) {
            throw new IllegalStateException(
                    "Quorum signing failed: " + acceptedSigners.size() + " of " + requiredSignatures + " signers responded.");
        }

        String combinedPsbt = bitcoinCore.combinePsbt(partialPsbts);
        BitcoinCoreRpcClient.FinalizedPsbt finalizedPsbt = bitcoinCore.finalizePsbt(combinedPsbt);
        if (!finalizedPsbt.complete() || finalizedPsbt.hex() == null || finalizedPsbt.hex().isBlank()) {
            throw new IllegalStateException("Combined PSBT could not be finalized.");
        }

        String txid = bitcoinCore.sendRawTransaction(finalizedPsbt.hex());
        if (txid == null || txid.isBlank()) {
            throw new IllegalStateException("Bitcoin Core did not return a txid after broadcast.");
        }

        networkTransferEventService.info(
                command.userId(),
                "PSBT_BROADCAST",
                txid,
                "signedBy=" + acceptedSigners.size() + " | destination=" + command.destinationAddress());

        return new OnchainExecution(
                txid,
                fundedPsbt.feeSats(),
                combinedPsbt,
                finalizedPsbt.hex(),
                acceptedSigners);
    }

    private String requestSignature(
            String signerUrl,
            String apiKey,
            String psbt,
            ExternalPaymentsCustodyPort.OnchainPaymentCommand command) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("psbt", psbt);
        payload.put("userId", command.userId());
        payload.put("walletId", command.walletId());
        payload.put("walletName", command.walletName());
        payload.put("destinationAddress", command.destinationAddress());
        payload.put("amountSats", command.amountSats());
        payload.put("authorizationProof", command.authorizationProof());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);
        ResponseEntity<String> response = restTemplate.postForEntity(signerUrl, request, String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Signer returned HTTP " + response.getStatusCode());
        }

        JsonNode body = objectMapper.readTree(response.getBody());
        JsonNode signedPsbt = body.path("signedPsbt");
        if (signedPsbt.isTextual() && !signedPsbt.asText().isBlank()) {
            return signedPsbt.asText();
        }
        JsonNode legacy = body.path("psbt");
        if (legacy.isTextual() && !legacy.asText().isBlank()) {
            return legacy.asText();
        }
        throw new IllegalStateException("Signer did not return a signed PSBT.");
    }

    private List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public record OnchainExecution(
            String txid,
            long feeSats,
            String combinedPsbt,
            String rawTransactionHex,
            List<String> acceptedSigners) {
    }
}
