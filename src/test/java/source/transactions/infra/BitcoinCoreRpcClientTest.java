package source.transactions.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BitcoinCoreRpcClientTest {

    @Test
    void executeNodeRpcPreservesBitcoinCoreErrorCodeAndMessage() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForEntity(
                eq("http://127.0.0.1:8332"),
                any(),
                eq(String.class))).thenReturn(ResponseEntity.ok("""
                        {"result":null,"error":{"code":-1,"message":"Block not available (pruned data)"},"id":"1"}
                        """));
        BitcoinCoreRpcClient client = new BitcoinCoreRpcClient(
                restTemplate,
                new ObjectMapper(),
                "http://127.0.0.1:8332",
                "user",
                "pass",
                "");

        BitcoinCoreRpcClient.BitcoinCoreRpcException exception = assertThrows(
                BitcoinCoreRpcClient.BitcoinCoreRpcException.class,
                () -> client.executeNodeRpc("getblock", "abc"));

        assertEquals("getblock", exception.method());
        assertEquals(-1, exception.code());
        assertTrue(exception.getMessage().contains("Block not available"));
    }
}
