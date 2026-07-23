package com.kerosene.auth.dto;

import java.util.List;

public record BackupCodesStatusDTO(
        boolean enabled,
        int remainingCodes,
        List<String> newlyGeneratedCodes) {
}
