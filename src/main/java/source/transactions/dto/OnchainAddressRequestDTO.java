package source.transactions.dto;

public record OnchainAddressRequestDTO(
        String walletName,
        Boolean regenerate) {
}
