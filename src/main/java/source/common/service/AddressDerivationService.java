package source.common.service;

import org.bitcoinj.core.Base58;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.common.infra.logging.LogSanitizer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Address derivation utilities for the Bitcoin flows.
 * Supports legacy deterministic fallback derivation plus BIP84 XPUB-based
 * address derivation for custodial and self-custody deposit flows.
 */
@Service
public class AddressDerivationService {

    private static final Logger log = LoggerFactory.getLogger(AddressDerivationService.class);

    private final NetworkParameters netParams;
    private final String derivationSalt;

    public AddressDerivationService(
            @Value("${bitcoin.network:mainnet}") String network,
            @Value("${bitcoin.derivation.salt:kerosene_sovereign_salt_2026}") String salt) {
        this.netParams = ("mainnet".equalsIgnoreCase(network) || "main".equalsIgnoreCase(network))
                ? MainNetParams.get()
                : TestNet3Params.get();
        this.derivationSalt = salt;
    }

    /**
     * Derives a unique P2WPKH (SegWit/bech32) address for a wallet.
     *
     * @param walletId Unique ID of the wallet
     * @param passphraseHash The Argon2id hash of the wallet passphrase (used as additional entropy)
     * @return A valid Bitcoin address string
     */
    public String deriveAddress(Long walletId, String passphraseHash) {
        try {
            // Create a deterministic seed for this specific wallet
            String seedSource = derivationSalt + ":" + walletId + ":" + passphraseHash;
            byte[] seed = MessageDigest.getInstance("SHA-256")
                    .digest(seedSource.getBytes(StandardCharsets.UTF_8));

            // Bitcoinj requires a public key hash (20 bytes) for P2WPKH.
            byte[] pubKeyHash = new byte[20];
            System.arraycopy(seed, 0, pubKeyHash, 0, 20);

            // In bitcoinj 0.15.10, we use SegwitAddress.fromHash for bech32 addresses
            SegwitAddress address = SegwitAddress.fromHash(netParams, pubKeyHash);

            String derived = address.toString();
            log.info("[Derivation] Derived Segwit addressRef={} for walletId={}",
                    LogSanitizer.fingerprint(derived), walletId);
            return derived;

        } catch (NoSuchAlgorithmException e) {
            log.error("[Derivation] Critical failure: SHA-256 algorithm not found");
            throw new RuntimeException("Address derivation failed", e);
        }
    }

    /**
     * Derives a P2WPKH address from an xpub at a given index.
     * Uses BIP84/Segwit (m/84'/0'/0'/0/index).
     */
    public String deriveAddressFromXpub(String xpub, int index) {
        return deriveAddressFromXpub(xpub, index, false);
    }

    public String deriveAddressFromXpub(String xpub, int index, boolean isChange) {
        return deriveAddressDetailsFromXpub(xpub, index, isChange).address();
    }

    public DerivedAddress deriveAddressDetailsFromXpub(String xpub, int index) {
        return deriveAddressDetailsFromXpub(xpub, index, false);
    }

    public DerivedAddress deriveAddressDetailsFromXpub(String xpub, int index, boolean isChange) {
        try {
            DeterministicKey masterKey = DeterministicKey.deserializeB58(normalizeExtendedPublicKey(xpub), netParams);

            // Derive child: m / <isChange> / <index>
            DeterministicKey childKey = HDKeyDerivation.deriveChildKey(
                    HDKeyDerivation.deriveChildKey(masterKey, isChange ? 1 : 0),
                    index);

            SegwitAddress address = SegwitAddress.fromHash(netParams, childKey.getPubKeyHash());
            return new DerivedAddress(address.toString(), childKey.getPubKey(), index, isChange);
        } catch (Exception e) {
            log.error("[Derivation] Failed to derive address from xpubRef={}: {}",
                    LogSanitizer.fingerprint(xpub), e.getMessage());
            throw new RuntimeException("XPub derivation failed", e);
        }
    }

    public String deriveAccountXpub(String mnemonic) {
        try {
            byte[] seed = org.bitcoinj.crypto.MnemonicCode.toSeed(
                    Arrays.asList(mnemonic.trim().split("\\s+")),
                    "");
            DeterministicKey masterKey = HDKeyDerivation.createMasterPrivateKey(seed);
            DeterministicKey purposeKey = HDKeyDerivation.deriveChildKey(masterKey,
                    new org.bitcoinj.crypto.ChildNumber(84, true));
            DeterministicKey coinTypeKey = HDKeyDerivation.deriveChildKey(
                    purposeKey,
                    new org.bitcoinj.crypto.ChildNumber(isMainnet() ? 0 : 1, true));
            DeterministicKey accountKey = HDKeyDerivation.deriveChildKey(
                    coinTypeKey,
                    org.bitcoinj.crypto.ChildNumber.ZERO_HARDENED);
            return accountKey.serializePubB58(netParams);
        } catch (Exception e) {
            log.error("[Derivation] Failed to derive account xpub from mnemonic: {}", e.getMessage());
            throw new RuntimeException("Account xpub derivation failed", e);
        }
    }

    public String deriveChildXpub(String parentXpub, int childIndex) {
        if (childIndex < 0) {
            throw new IllegalArgumentException("Child index must be non-negative.");
        }

        try {
            DeterministicKey parentKey = DeterministicKey.deserializeB58(normalizeExtendedPublicKey(parentXpub), netParams);
            DeterministicKey childKey = HDKeyDerivation.deriveChildKey(parentKey, childIndex);
            return childKey.serializePubB58(netParams);
        } catch (Exception e) {
            log.error("[Derivation] Failed to derive child xpub from parentRef={}: {}",
                    LogSanitizer.fingerprint(parentXpub), e.getMessage());
            throw new RuntimeException("Child xpub derivation failed", e);
        }
    }

    private String normalizeExtendedPublicKey(String rawXpub) {
        if (rawXpub == null || rawXpub.isBlank()) {
            throw new IllegalArgumentException("XPUB is required.");
        }

        String xpub = rawXpub.trim();
        if (xpub.startsWith("xpub") || xpub.startsWith("tpub")) {
            return xpub;
        }

        byte[] decoded = Base58.decodeChecked(xpub);
        if (decoded.length < 4) {
            return xpub;
        }

        int replacementVersion;
        if (startsWith(decoded, 0x04, 0xb2, 0x47, 0x46) || startsWith(decoded, 0x04, 0x9d, 0x7c, 0xb2)) {
            replacementVersion = 0x0488B21E;
        } else if (startsWith(decoded, 0x04, 0x5f, 0x1c, 0xf6) || startsWith(decoded, 0x04, 0x4a, 0x52, 0x62)) {
            replacementVersion = 0x043587CF;
        } else {
            return xpub;
        }

        byte[] payload = Arrays.copyOf(decoded, decoded.length);
        payload[0] = (byte) ((replacementVersion >> 24) & 0xff);
        payload[1] = (byte) ((replacementVersion >> 16) & 0xff);
        payload[2] = (byte) ((replacementVersion >> 8) & 0xff);
        payload[3] = (byte) (replacementVersion & 0xff);

        byte[] checksum = Arrays.copyOf(Sha256Hash.hashTwice(payload), 4);
        byte[] encoded = Arrays.copyOf(payload, payload.length + 4);
        System.arraycopy(checksum, 0, encoded, payload.length, 4);
        return Base58.encode(encoded);
    }

    private boolean startsWith(byte[] value, int b0, int b1, int b2, int b3) {
        return value.length >= 4
                && (value[0] & 0xff) == b0
                && (value[1] & 0xff) == b1
                && (value[2] & 0xff) == b2
                && (value[3] & 0xff) == b3;
    }

    private boolean isMainnet() {
        return netParams instanceof MainNetParams;
    }

    public record DerivedAddress(
            String address,
            byte[] publicKey,
            int index,
            boolean change) {
    }
}
