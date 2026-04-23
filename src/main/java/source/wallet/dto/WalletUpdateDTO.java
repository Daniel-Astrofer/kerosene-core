package source.wallet.dto;

import jakarta.validation.constraints.NotBlank;

public record WalletUpdateDTO(
        @NotBlank(message = "A passphrase é obrigatória para autorizar a modificação") String passphrase,

        @NotBlank(message = "O nome atual da carteira é obrigatório") String name,

        String newName,

        String newXpub) {
}
