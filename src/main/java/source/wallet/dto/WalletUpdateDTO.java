package source.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WalletUpdateDTO(
        @NotBlank(message = "A passphrase é obrigatória para autorizar a modificação") String passphrase,

        @NotBlank(message = "O nome atual da carteira é obrigatório") String name,

        @NotBlank(message = "O novo nome é obrigatório") @Size(min = 3, max = 50, message = "O novo nome deve ter entre 3 e 50 caracteres") String newName) {
}
