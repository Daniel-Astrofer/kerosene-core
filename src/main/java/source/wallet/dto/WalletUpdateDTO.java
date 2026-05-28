package source.wallet.dto;

import jakarta.validation.constraints.NotBlank;

public record WalletUpdateDTO(
        @NotBlank(message = "A passphrase é obrigatória para autorizar a modificação") String passphrase,

        @NotBlank(message = "O nome atual da carteira é obrigatório") String name,

        String newName,

        String newXpub,

        String newWalletMode) {

    public WalletUpdateDTO(String passphrase, String name, String newName, String newXpub) {
        this(passphrase, name, newName, newXpub, null);
    }
}
