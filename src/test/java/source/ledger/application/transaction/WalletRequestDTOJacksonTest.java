package source.ledger.application.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import source.wallet.dto.WalletRequestDTO;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WalletRequestDTOJacksonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldIgnoreUnknownFieldsFromWalletCreatePayload() throws Exception {
        String payload = """
                {
                  "name": "ds",
                  "passphrase": "test-passphrase-bip39",
                  "accountSecurity": "STANDARD",
                  "xpub": null,
                  "walletMode": "KEROSENE"
                }
                """;

        WalletRequestDTO dto = objectMapper.readValue(payload, WalletRequestDTO.class);

        assertEquals("ds", dto.name());
        assertArrayEquals("test-passphrase-bip39".toCharArray(), dto.passphrase());
        assertNull(dto.xpub());
        assertEquals("KEROSENE", dto.walletMode());
    }
}
