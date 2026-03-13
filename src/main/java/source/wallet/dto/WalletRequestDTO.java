package source.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WalletRequestDTO(
        @NotBlank(message = "A passphrase é obrigatória") String passphrase,

        @NotBlank(message = "O nome da carteira é obrigatório") @Size(min = 3, max = 50, message = "O nome deve ter entre 3 e 50 caracteres") String name) {
}
