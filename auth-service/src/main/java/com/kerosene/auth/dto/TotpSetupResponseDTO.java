package com.kerosene.auth.dto;

public record TotpSetupResponseDTO(
        String otpUri,
        String secret) {
}
