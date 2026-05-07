package source.wallet.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;

class WalletResponseDTOTest {

    @Test
    void shouldNotExposePassphraseHashInJson() throws Exception {
        WalletResponseDTO dto = new WalletResponseDTO(
                1L,
                "MAIN",
                LocalDateTime.now(),
                LocalDateTime.now(),
                true,
                null,
                "bc1qwallet",
                null,
                "KEROSENE",
                false,
                "BRONZE",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("0.0090"),
                new BigDecimal("0.0090"));

        String json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .writeValueAsString(dto);

        assertFalse(json.contains("passphraseHash"));
        assertFalse(json.contains("secret"));
        assertFalse(json.contains("seed"));
    }
}
