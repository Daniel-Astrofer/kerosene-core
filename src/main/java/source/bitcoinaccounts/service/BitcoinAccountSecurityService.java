package source.bitcoinaccounts.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class BitcoinAccountSecurityService {

    private static final Pattern EXTENDED_PRIVATE_KEY = Pattern.compile("(?i)\\b([xtuvyz]prv)[1-9A-HJ-NP-Za-km-z]{40,}\\b");
    private static final Pattern EXTENDED_PUBLIC_KEY = Pattern.compile("(?i)\\b([xtuvyz]pub)[1-9A-HJ-NP-Za-km-z]{40,}\\b");
    private static final Pattern WIF_PRIVATE_KEY = Pattern.compile("\\b[5KL][1-9A-HJ-NP-Za-km-z]{50,51}\\b");
    private static final Pattern RAW_PRIVATE_KEY = Pattern.compile("(?i)\\b[0-9a-f]{64}\\b");
    private static final Pattern MASTER_FINGERPRINT = Pattern.compile("(?i)^[0-9a-f]{8}$");
    private static final Pattern DERIVATION_PATH = Pattern.compile("^m(/[0-9]+['hH]?){1,10}$");
    private static final List<String> SECRET_FIELD_HINTS = List.of(
            "seed",
            "mnemonic",
            "passphrase",
            "recovery phrase",
            "private key",
            "xprv",
            "yprv",
            "zprv",
            "tprv");

    public void rejectSecretMaterial(String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (EXTENDED_PRIVATE_KEY.matcher(normalized).find()
                || WIF_PRIVATE_KEY.matcher(normalized).find()
                || looksLikeMnemonic(normalized)
                || containsSecretFieldHint(lower)) {
            throw new IllegalArgumentException(
                    "A Kerosene não recebe seed, mnemonic, passphrase, xprv ou chave privada. Importe somente descriptor/xpub watch-only.");
        }
        if (RAW_PRIVATE_KEY.matcher(normalized).matches() && !"fingerprint".equalsIgnoreCase(label)) {
            throw new IllegalArgumentException(
                    "Esse valor parece material de chave privada. Use somente dados públicos watch-only.");
        }
    }

    public void validatePublicWatchOnlyMaterial(String descriptor, String xpub) {
        rejectSecretMaterial("descriptor", descriptor);
        rejectSecretMaterial("xpub", xpub);
        if ((descriptor == null || descriptor.isBlank()) && (xpub == null || xpub.isBlank())) {
            throw new IllegalArgumentException("Informe um descriptor watch-only ou xpub para monitorar a carteira.");
        }
        if (xpub != null && !xpub.isBlank() && !EXTENDED_PUBLIC_KEY.matcher(xpub.trim()).find()) {
            throw new IllegalArgumentException("Informe um xpub/ypub/zpub/tpub público válido para modo watch-only.");
        }
    }

    public void validateColdWalletMetadata(String fingerprint, String derivationPath) {
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("Fingerprint is required to import a watch-only wallet.");
        }
        if (!MASTER_FINGERPRINT.matcher(fingerprint.trim()).matches()) {
            throw new IllegalArgumentException("Fingerprint must use exactly 8 hexadecimal characters.");
        }
        if (derivationPath == null || derivationPath.isBlank()) {
            throw new IllegalArgumentException("Derivation path is required to import a watch-only wallet.");
        }
        if (!DERIVATION_PATH.matcher(derivationPath.trim()).matches()) {
            throw new IllegalArgumentException("Derivation path must look like m/84'/0'/0'.");
        }
    }

    private boolean containsSecretFieldHint(String value) {
        return SECRET_FIELD_HINTS.stream().anyMatch(hint -> value.contains(hint + "=") || value.contains("\"" + hint + "\""));
    }

    private boolean looksLikeMnemonic(String value) {
        String[] words = value.trim().split("\\s+");
        if (words.length != 12 && words.length != 15 && words.length != 18 && words.length != 21 && words.length != 24) {
            return false;
        }
        int plainWords = 0;
        for (String word : words) {
            if (word.matches("[a-zA-Z]{2,12}")) {
                plainWords++;
            }
        }
        return plainWords == words.length;
    }
}
