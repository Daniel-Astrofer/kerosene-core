package source.transactions.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class BlockchainInfoClient {

    private static final String API_BASE = "https://blockchain.info";
    private final String apiKey;
    private final RestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean mockMode;

    public BlockchainInfoClient(@Value("${blockchain.info.api-key:}") String apiKey,
            @Value("${bitcoin.mock-mode:false}") boolean mockMode) {
        this.apiKey = apiKey;
        this.rest = new RestTemplate();
        this.mockMode = mockMode;

        if (mockMode) {
            System.out.println("⚠️  MODO MOCK ATIVADO - Não fará chamadas reais à API");
        }
    }

    public String sendTransaction(String fromAddress, String toAddress, BigDecimal amountBtc) {
        // Blockchain.info push_tx requires a signed raw transaction hex
        // For a simple flow, we simulate using their paid API
        // In production, sign tx client-side and push here
        try {
            if (apiKey == null || apiKey.isEmpty()) {
                return "mock-tx-" + System.currentTimeMillis();
            }
            // Push signed tx: POST /pushtx with param tx=<hex>
            // This is a placeholder - real implementation needs signed hex
            String url = API_BASE + "/pushtx?tx=<signed_hex>&api_code=" + apiKey;
            System.out.println("⚠️  To send real tx via Blockchain.info, sign tx client-side and call: " + url);
            return "mock-tx-" + System.currentTimeMillis();
        } catch (Exception e) {
            System.err.println("Blockchain.info send failed: " + e.getMessage());
            return "mock-tx-" + System.currentTimeMillis();
        }
    }

    public JsonNode getTransactionInfo(String txid) {
        // Support for Mock Mode
        if (mockMode && (txid.startsWith("mock-tx"))) {
            try {
                // Return a simulated confirmed transaction JSON
                ObjectMapper mockMapper = new ObjectMapper();
                var mockNode = mockMapper.createObjectNode();
                mockNode.put("hash", txid);
                mockNode.put("ver", 1);
                mockNode.put("block_height", 800000); // Simulates confirmed tx (non-null block_height)
                mockNode.put("time", System.currentTimeMillis() / 1000);
                mockNode.put("fee", 5000);

                var outArray = mockNode.putArray("out");
                var outItem = outArray.addObject();
                outItem.put("value", 100000);
                outItem.put("addr", "mock-address");

                var inputsArray = mockNode.putArray("inputs");
                var inputItem = inputsArray.addObject();
                inputItem.put("sequence", 4294967295L);

                return mockNode;
            } catch (Exception e) {
                System.err.println("Error creating mock transaction info: " + e.getMessage());
                return null;
            }
        }

        try {
            String url = API_BASE + "/rawtx/" + txid;
            if (apiKey != null && !apiKey.isEmpty()) {
                url += "?api_code=" + apiKey;
            }
            ResponseEntity<String> resp = rest.getForEntity(url, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                return null;
            }
            JsonNode tree = mapper.readTree(resp.getBody());
            return tree;
        } catch (Exception e) {
            System.err.println("Blockchain.info query failed: " + e.getMessage());
            return null;
        }
    }

    public Map<String, Object> getTransaction(String txid) {
        try {
            JsonNode jsonNode = getTransactionInfo(txid);
            if (jsonNode == null) {
                return null;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("txid", txid);

            // Extrair confirmações
            int confirmations = 0;
            if (jsonNode.has("block_height") && !jsonNode.get("block_height").isNull()) {
                confirmations = 1; // Simplificado - na prática calcular: current_height - block_height + 1
            }
            result.put("confirmations", confirmations);

            // Extrair fee
            if (jsonNode.has("fee")) {
                result.put("fee", jsonNode.get("fee").asLong(0L));
            }

            return result;
        } catch (Exception e) {
            System.err.println("Failed to get transaction: " + e.getMessage());
            return null;
        }
    }

    public String getAddressBalance(String address) {
        try {
            String url = API_BASE + "/q/addressbalance/" + address;
            if (apiKey != null && !apiKey.isEmpty()) {
                url += "?api_code=" + apiKey;
            }
            ResponseEntity<String> resp = rest.getForEntity(url, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                return "0";
            }
            return resp.getBody();
        } catch (Exception e) {
            System.err.println("Blockchain.info balance query failed: " + e.getMessage());
            return "0";
        }
    }

    public String pushSignedTransaction(String rawTxHex) {
        if (mockMode) {
            System.out.println("⚠️  [MOCK] Push Transaction: " + rawTxHex);
            return "mock-txid-" + java.util.UUID.randomUUID().toString();
        }
        try {
            String url = API_BASE + "/pushtx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String body = "tx=" + rawTxHex;
            if (apiKey != null && !apiKey.isEmpty()) {
                body += "&api_code=" + apiKey;
            }

            HttpEntity<String> req = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = rest.postForEntity(url, req, String.class);

            if (!resp.getStatusCode().is2xxSuccessful()) {
                System.err.println("Failed to push TX: " + resp.getStatusCode());
                return null;
            }

            // Blockchain.info returns txid on success
            return resp.getBody();
        } catch (Exception e) {
            System.err.println("Blockchain.info push signed tx failed: " + e.getMessage());
            return null;
        }
    }

    public JsonNode getRecommendedFees() {
        try {
            // Usar Mempool.space API para obter taxas recomendadas
            String url = "https://mempool.space/api/v1/fees/recommended";
            ResponseEntity<String> resp = rest.getForEntity(url, String.class);

            if (!resp.getStatusCode().is2xxSuccessful()) {
                return null;
            }

            JsonNode tree = mapper.readTree(resp.getBody());
            return tree;
        } catch (Exception e) {
            System.err.println("Failed to fetch recommended fees: " + e.getMessage());
            return null;
        }
    }

    public boolean validateDepositTransaction(String txid, String expectedToAddress, BigDecimal expectedAmount) {
        try {
            // MODO MOCK
            if (mockMode) {
                return validateDepositMock(txid, expectedToAddress, expectedAmount);
            }

            // Consultar TX na blockchain
            JsonNode txInfo = getTransactionInfo(txid);
            if (txInfo == null) {
                System.err.println("⚠️  TX não encontrada: " + txid);
                return false;
            }

            // Validar que enviou para o endereço correto
            if (!txInfo.has("out")) {
                System.err.println("⚠️  TX não tem outputs");
                return false;
            }

            JsonNode outputs = txInfo.get("out");
            boolean foundCorrectOutput = false;
            double totalReceived = 0;

            for (JsonNode output : outputs) {
                if (output.has("addr") && output.get("addr").asText().equals(expectedToAddress)) {
                    double satoshis = output.get("value").asDouble(0);
                    double btc = satoshis / 1e8;
                    totalReceived += btc;
                    foundCorrectOutput = true;
                }
            }

            if (!foundCorrectOutput) {
                System.err.println("⚠️  TX não enviou para o endereço esperado");
                return false;
            }

            // Validar que o valor é pelo menos o esperado
            if (totalReceived < expectedAmount.doubleValue()) {
                System.err.println(
                        "⚠️  Valor recebido (" + totalReceived + ") menor que esperado (" + expectedAmount + ")");
                return false;
            }

            // Validar que TX está assinada (tem inputs válidos)
            if (!txInfo.has("inputs") || txInfo.get("inputs").size() == 0) {
                System.err.println("⚠️  TX não tem inputs válidos (não foi assinada)");
                return false;
            }

            System.out.println("✅ TX validada: " + txid);
            System.out.println("   Endereço: " + expectedToAddress);
            System.out.println("   Valor recebido: " + totalReceived + " BTC");
            System.out.println(
                    "   Confirmações: " + (txInfo.has("block_height") && !txInfo.get("block_height").isNull() ? 1 : 0));

            return true;

        } catch (Exception e) {
            System.err.println("Erro ao validar TX: " + e.getMessage());
            return false;
        }
    }

    private boolean validateDepositMock(String txid, String expectedToAddress, BigDecimal expectedAmount) {
        // Validações básicas no modo mock
        if (txid == null || txid.isEmpty()) {
            System.err.println("⚠️  TXID inválido");
            return false;
        }

        if (expectedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            System.err.println("⚠️  Valor deve ser positivo");
            return false;
        }

        // Simular validação bem-sucedida
        System.out.println("✅ [MOCK] TX validada: " + txid);
        System.out.println("   Endereço: " + expectedToAddress);
        System.out.println("   Valor recebido: " + expectedAmount + " BTC");
        System.out.println("   Confirmações: 1 (simulado)");

        return true;
    }
}
