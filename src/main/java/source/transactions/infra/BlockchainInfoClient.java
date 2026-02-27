package source.transactions.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Unified Blockchain Client that delegates to Alchemy for Node RPC
 * and Mempool.space for indexed address data.
 */
@Component
public class BlockchainInfoClient {

    private final AlchemyClient alchemyClient;
    private final RestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();

    public BlockchainInfoClient(AlchemyClient alchemyClient) {
        this.alchemyClient = alchemyClient;
        this.rest = new RestTemplate();
    }

    /**
     * @deprecated Use pushSignedTransaction with actual signed hex from client.
     */
    @Deprecated
    public String sendTransaction(String fromAddress, String toAddress, BigDecimal amountBtc) {
        System.out.println("⚠️  sendTransaction is deprecated. Use pushSignedTransaction.");
        return null;
    }

    public JsonNode getTransactionInfo(String txid) {
        if (!isValidTxid(txid)) {
            System.err.println("⚠️  Invalid TXID format: " + txid);
            return null;
        }
        // Use Alchemy JSON-RPC getrawtransaction (verbose=1)
        return alchemyClient.getRawTransaction(txid, true);
    }

    private boolean isValidTxid(String txid) {
        return txid != null && txid.matches("^[a-fA-F0-9]{64}$");
    }

    public Map<String, Object> getTransaction(String txid) {
        try {
            JsonNode jsonNode = getTransactionInfo(txid);
            if (jsonNode == null) {
                return null;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("txid", txid);

            // Alchemy returns blockheight/confirmations differently
            int confirmations = 0;
            if (jsonNode.has("confirmations")) {
                confirmations = jsonNode.get("confirmations").asInt(0);
            }
            result.put("confirmations", confirmations);

            // Extrair fee (Alchemy getrawtransaction verbose=1 includes fee in some
            // versions/nodes,
            // otherwise we'd need gettxout or mempool)
            if (jsonNode.has("fee")) {
                result.put("fee", (long) (jsonNode.get("fee").asDouble(0) * 1e8));
            }

            return result;
        } catch (Exception e) {
            System.err.println("Failed to get transaction: " + e.getMessage());
            return null;
        }
    }

    public String getAddressBalance(String address) {
        try {
            // Use Mempool.space for address balance (indexed data)
            String url = "https://mempool.space/api/address/" + address;
            ResponseEntity<String> resp = rest.getForEntity(url, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                return "0";
            }
            JsonNode node = mapper.readTree(resp.getBody());
            long funded = node.get("chain_stats").get("funded_txo_sum").asLong(0);
            long spent = node.get("chain_stats").get("spent_txo_sum").asLong(0);
            return String.valueOf(funded - spent);
        } catch (Exception e) {
            System.err.println("Mempool.space balance query failed: " + e.getMessage());
            return "0";
        }
    }

    public String pushSignedTransaction(String rawTxHex) {
        // Use Alchemy JSON-RPC sendrawtransaction
        return alchemyClient.sendRawTransaction(rawTxHex);
    }

    public JsonNode getRecommendedFees() {
        try {
            // Mempool.space is the standard for fee estimation
            String url = "https://mempool.space/api/v1/fees/recommended";
            ResponseEntity<String> resp = rest.getForEntity(url, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                return null;
            }
            return mapper.readTree(resp.getBody());
        } catch (Exception e) {
            System.err.println("Failed to fetch recommended fees: " + e.getMessage());
            return null;
        }
    }

    public boolean validateDepositTransaction(String txid, String expectedToAddress, BigDecimal expectedAmount) {
        try {
            JsonNode txInfo = getTransactionInfo(txid);
            if (txInfo == null) {
                System.err.println("⚠️  TX não encontrada no Alchemy: " + txid);
                return false;
            }

            if (!txInfo.has("vout")) {
                System.err.println("⚠️  TX não tem outputs (vout)");
                return false;
            }

            JsonNode outputs = txInfo.get("vout");
            boolean foundCorrectOutput = false;
            double totalReceived = 0;

            for (JsonNode output : outputs) {
                JsonNode scriptPubKey = output.get("scriptPubKey");
                if (scriptPubKey != null && scriptPubKey.has("address")) {
                    String addr = scriptPubKey.get("address").asText();
                    if (addr.equals(expectedToAddress)) {
                        double valueBtc = output.get("value").asDouble(0);
                        totalReceived += valueBtc;
                        foundCorrectOutput = true;
                    }
                }
            }

            if (!foundCorrectOutput) {
                System.err.println("⚠️  TX não enviou para o endereço esperado: " + expectedToAddress);
                return false;
            }

            if (totalReceived < expectedAmount.doubleValue()) {
                System.err.println("⚠️  Valor insuficiente: " + totalReceived + " < " + expectedAmount);
                return false;
            }

            return true;
        } catch (Exception e) {
            System.err.println("Erro ao validar TX: " + e.getMessage());
            return false;
        }
    }

    /**
     * Fetches the real-time BTC to BRL price from Binance public API.
     * Returns the price as a BigDecimal. Returns null if the request fails.
     */
    public BigDecimal getBtcPriceInBrl() {
        try {
            String url = "https://api.binance.com/api/v3/ticker/price?symbol=BTCBRL";
            ResponseEntity<String> resp = rest.getForEntity(url, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                System.err.println("❌ Erro ao buscar preço BTC/BRL: HTTP " + resp.getStatusCode());
                return null;
            }
            JsonNode node = mapper.readTree(resp.getBody());
            String priceStr = node.get("price").asText();
            return new BigDecimal(priceStr);
        } catch (Exception e) {
            System.err.println("❌ Erro ao conectar com Binance API: " + e.getMessage());
            return null;
        }
    }
}
