package source.wallet.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletRequestDTO(
        @NotBlank(message = "A passphrase é obrigatória") char[] passphrase,

        @NotBlank(message = "O nome da carteira é obrigatório") @Size(min = 3, max = 50, message = "O nome deve ter entre 3 e 50 caracteres") String name,

        String xpub,

        String walletMode) {

    public WalletRequestDTO(char[] passphrase, String name, String xpub) {
        this(passphrase, name, xpub, null);
    }
}
