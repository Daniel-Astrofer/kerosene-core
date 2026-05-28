package source.auth.application.service.recovery;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import source.auth.application.service.cripto.contracts.Hasher;

@Service
public class RecoveryCodeService {

    private static final Pattern RECOVERY_CODE_PATTERN = Pattern.compile("^\\d{8}$");
    private static final int NEW_BACKUP_CODE_COUNT = 10;

    private final Hasher hasher;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String dummyRecoveryHash;

    public RecoveryCodeService(@Qualifier("Argon2Hasher") Hasher hasher) {
        this.hasher = hasher;
        char[] dummyCode = "00000000".toCharArray();
        try {
            this.dummyRecoveryHash = hasher.hash(dummyCode);
        } finally {
            java.util.Arrays.fill(dummyCode, '\0');
        }
    }

    public List<String> normalizeRecoveryCodes(List<String> recoveryCodes) {
        if (recoveryCodes == null) {
            throw new IllegalArgumentException("Recovery codes are required.");
        }

        Set<String> distinctCodes = new LinkedHashSet<>();
        for (String rawCode : recoveryCodes) {
            if (rawCode == null) {
                throw new IllegalArgumentException("Recovery codes cannot contain null values.");
            }
            String normalized = rawCode.trim();
            if (!RECOVERY_CODE_PATTERN.matcher(normalized).matches()) {
                throw new IllegalArgumentException("Recovery codes must be 8 numeric digits.");
            }
            distinctCodes.add(normalized);
        }
        return new ArrayList<>(distinctCodes);
    }

    public List<String> matchRecoveryCodes(List<String> submittedCodes, List<String> storedHashes) {
        List<String> matchedHashes = new ArrayList<>();
        boolean[] consumed = new boolean[storedHashes.size()];

        for (String code : submittedCodes) {
            boolean matched = false;
            char[] candidate = code.toCharArray();
            try {
                for (int i = 0; i < storedHashes.size(); i++) {
                    if (consumed[i]) {
                        continue;
                    }
                    if (Boolean.TRUE.equals(hasher.verify(candidate, storedHashes.get(i)))) {
                        consumed[i] = true;
                        matchedHashes.add(storedHashes.get(i));
                        matched = true;
                        break;
                    }
                }
            } finally {
                java.util.Arrays.fill(candidate, '\0');
            }
            if (!matched) {
                return List.of();
            }
        }

        return matchedHashes;
    }

    public void burnRecoveryCodeChecks(List<String> submittedCodes) {
        for (String code : submittedCodes) {
            char[] candidate = code.toCharArray();
            try {
                hasher.verify(candidate, dummyRecoveryHash);
            } finally {
                java.util.Arrays.fill(candidate, '\0');
            }
        }
    }

    public GeneratedRecoveryCodes generateNewBackupCodes() {
        List<String> rawCodes = new ArrayList<>(NEW_BACKUP_CODE_COUNT);
        List<String> hashedCodes = new ArrayList<>(NEW_BACKUP_CODE_COUNT);

        for (int i = 0; i < NEW_BACKUP_CODE_COUNT; i++) {
            String code = String.format("%08d", secureRandom.nextInt(100000000));
            rawCodes.add(code);

            char[] candidate = code.toCharArray();
            try {
                hashedCodes.add(hasher.hash(candidate));
            } finally {
                java.util.Arrays.fill(candidate, '\0');
            }
        }

        return new GeneratedRecoveryCodes(rawCodes, hashedCodes);
    }

    public record GeneratedRecoveryCodes(List<String> rawCodes, List<String> hashedCodes) {
    }
}
