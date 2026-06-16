package source.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WalletUpdateDTO(
        @NotBlank(message = "A passphrase é obrigatória para autorizar a modificação") char[] passphrase,

        @NotBlank(message = "O nome atual da carteira é obrigatório") String name,

        @Size(min = 3, max = 50, message = "O novo nome deve ter entre 3 e 50 caracteres") String newName,

        String newXpub,

        String newWalletMode) {

    public WalletUpdateDTO(char[] passphrase, String name, String newName, String newXpub) {
        this(passphrase, name, newName, newXpub, null);
    }
}
