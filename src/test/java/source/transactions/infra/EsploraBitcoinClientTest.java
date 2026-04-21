package source.transactions.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.params.MainNetParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import source.common.service.AddressDerivationService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EsploraBitcoinClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, StubResponse> responses = new ConcurrentHashMap<>();
    private final List<CapturedRequest> requests = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handleRequest);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendRawTransactionPostsHexAndReturnsTxid() {
        responses.put("POST /api/tx", StubResponse.text(200, "abc123"));

        EsploraBitcoinClient client = newClient("", "", 128);

        assertEquals("abc123", client.sendRawTransaction("deadbeef"));
        assertEquals("deadbeef", requests.getFirst().body());
    }

    @Test
    void getRawTransactionAddsConfirmationsFromTipHeight() throws Exception {
        responses.put("GET /api/tx/tx-1", StubResponse.json(objectMapper.writeValueAsString(Map.of(
                "txid", "tx-1",
                "status", Map.of(
                        "confirmed", true,
                        "block_height", 100L)))));
        responses.put("GET /api/blocks/tip/height", StubResponse.text(200, "105"));

        EsploraBitcoinClient client = newClient("", "", 128);

        assertEquals(6, client.getRawTransaction("tx-1", true).path("confirmations").asInt());
    }

    @Test
    void estimateSmartFeeUsesNearestAvailableTargets() throws Exception {
        responses.put("GET /api/fee-estimates", StubResponse.json(objectMapper.writeValueAsString(Map.of(
                "1", 87.882,
                "6", 68.285,
                "24", 2.1,
                "144", 1.027))));

        EsploraBitcoinClient client = newClient("", "", 128);
        BlockchainClient.FeeRates fees = client.estimateSmartFee(1, 6, 24);

        assertEquals(88L, fees.fastSatPerVByte());
        assertEquals(69L, fees.halfHourSatPerVByte());
        assertEquals(3L, fees.hourSatPerVByte());
    }

    @Test
    void getConfirmedBalanceForXpubSumsExternalAndChangeBranches() throws Exception {
        String xpub = createXpub();
        AddressDerivationService derivationService = new AddressDerivationService("mainnet", "kerosene-test-salt");
        String externalAddress = derivationService.deriveAddressFromXpub(xpub, 0, false);
        String changeAddress = derivationService.deriveAddressFromXpub(xpub, 0, true);

        responses.put("GET /api/address/" + externalAddress, StubResponse.json(objectMapper.writeValueAsString(Map.of(
                "chain_stats", Map.of(
                        "funded_txo_sum", 2_500L,
                        "spent_txo_sum", 1_000L)))));
        responses.put("GET /api/address/" + changeAddress, StubResponse.json(objectMapper.writeValueAsString(Map.of(
                "chain_stats", Map.of(
                        "funded_txo_sum", 1_000L,
                        "spent_txo_sum", 400L)))));

        EsploraBitcoinClient client = new EsploraBitcoinClient(
                baseUrl,
                "mainnet",
                "",
                "",
                128,
                false,
                new RestTemplate(),
                objectMapper,
                derivationService);

        assertEquals(2_100L, client.getConfirmedBalanceForXpub(xpub, 1, true));
    }

    private EsploraBitcoinClient newClient(String hotWalletAddress, String hotWalletXpub, int hotWalletXpubScanRange) {
        return new EsploraBitcoinClient(
                baseUrl,
                "mainnet",
                hotWalletAddress,
                hotWalletXpub,
                hotWalletXpubScanRange,
                false,
                new RestTemplate(),
                objectMapper,
                new AddressDerivationService("mainnet", "kerosene-test-salt"));
    }

    private String createXpub() {
        byte[] seed = "kerosene-esplora-test-seed".getBytes(StandardCharsets.UTF_8);
        DeterministicKey master = HDKeyDerivation.createMasterPrivateKey(seed);
        return master.serializePubB58(MainNetParams.get());
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
        requests.add(new CapturedRequest(key, body));

        StubResponse response = responses.get(key);
        if (response == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", response.contentType());
        byte[] payload = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.status(), payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private record StubResponse(int status, String contentType, String body) {
        static StubResponse json(String body) {
            return new StubResponse(200, "application/json", body);
        }

        static StubResponse text(int status, String body) {
            return new StubResponse(status, "text/plain", body);
        }
    }

    private record CapturedRequest(String key, String body) {
    }
}
