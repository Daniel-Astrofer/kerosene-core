package com.kerosene.auth.application.service.recovery;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.kerosene.auth.application.service.crypto.contracts.Hasher;

/** Normalizes, matches, burns, and rotates numeric emergency recovery codes. */
@Service
public class RecoveryCodeService {

    /** Required syntax for each recovery code: exactly eight decimal digits. */
    private static final Pattern RECOVERY_CODE_PATTERN = Pattern.compile("^\\d{8}$");
    /** Number of replacement recovery codes generated after successful recovery. */
    private static final int NEW_BACKUP_CODE_COUNT = 10;

    /** Argon2-qualified hash/verification operation for codes. */
    private final Hasher hasher;
    /** Cryptographically secure code generation source. */
    private final SecureRandom secureRandom = new SecureRandom();
    /** Precomputed hash used to perform equivalent hash work for ineligible accounts. */
    private final String dummyRecoveryHash;

    /** Creates the code service and builds its dummy verification hash once. */
    /** @param hasher Argon2 code hasher/verifier */
    public RecoveryCodeService(@Qualifier("Argon2Hasher") Hasher hasher) {
        this.hasher = hasher;
        char[] dummyCode = "00000000".toCharArray();
        try {
            this.dummyRecoveryHash = hasher.hash(dummyCode);
        } finally {
            java.util.Arrays.fill(dummyCode, '\0');
        }
    }

    /** Trims, validates, and de-duplicates submitted eight-digit codes while preserving first-seen order. */
    /** @param recoveryCodes client-supplied codes */
    /** @return distinct normalized codes */
    /** @throws IllegalArgumentException when the list or any item is absent/malformed */
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

    /** Matches each submitted code to a distinct stored hash and stops at the first unmatched candidate. */
    /** @param submittedCodes normalized client values */
    /** @param storedHashes persisted code hashes */
    /** @return matching hashes in request order, or empty list when any candidate fails */
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

    /** Performs one dummy hash verification per submitted code to reduce account-eligibility timing differences. */
    /** @param submittedCodes normalized candidate values */
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

    /** Generates ten random eight-digit codes, returning raw values once and retaining only hashes for storage. */
    /** @return generated raw and hashed code lists */
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

    /**
     * One-time raw recovery codes paired with their persisted hash values.
     * @param rawCodes values shown once to the user
     * @param hashedCodes values saved on the account
     */
    public record GeneratedRecoveryCodes(List<String> rawCodes, List<String> hashedCodes) {
    }
}
