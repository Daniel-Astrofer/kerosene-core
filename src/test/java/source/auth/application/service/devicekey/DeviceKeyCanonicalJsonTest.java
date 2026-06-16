package source.auth.application.service.devicekey;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeviceKeyCanonicalJsonTest {

    private static final byte[] ED25519_X509_PREFIX = new byte[] {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private static final String VECTOR_PUBLIC_KEY =
            "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo";
    private static final String VECTOR_SIGNATURE =
            "73edKfMLbwJQhvw1bmH58JlX_z8h-7FJF3gzU4INb0wgwG3h2WOShoPAHbUlOLV76MLQUUIOyliHQrX-GUQtCA";
    private static final String VECTOR_PAYLOAD =
            "{\"algorithm\":\"Ed25519\",\"challenge\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"challengeId\":\"11111111-2222-3333-4444-555555555555\",\"counter\":1,\"credentialId\":\"PLgXnnKy4uLf-5HloFqIqTOTkE41AY1uQaBevvikXCg\",\"deviceInstallId\":\"device-install-0001\",\"issuedAtEpochSeconds\":1234567890,\"onionServiceId\":\"kerosene-device\",\"publicKeySha256\":\"If4x36FUomFia_hUBG_SJxt77UtqvkWqWId-9H-XIbk\",\"sessionId\":\"signup-session-1\",\"type\":\"REGISTER_DEVICE_KEY\",\"username\":\"alice\",\"version\":1}";

    @Test
    void canonicalJsonSortsKeysAndRemovesSpaces() {
        String canonical = DeviceKeyCanonicalJson.canonicalize(Map.of(
                "username", "alice",
                "version", 1,
                "type", "REGISTER_DEVICE_KEY",
                "counter", 1,
                "challenge", "abc"));

        assertEquals(
                "{\"challenge\":\"abc\",\"counter\":1,\"type\":\"REGISTER_DEVICE_KEY\",\"username\":\"alice\",\"version\":1}",
                canonical);
    }

    @Test
    void fixedVectorSignatureVerifies() throws Exception {
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(loadRawPublicKey(decodeBase64Url(VECTOR_PUBLIC_KEY)));
        verifier.update(VECTOR_PAYLOAD.getBytes(StandardCharsets.UTF_8));

        assertTrue(verifier.verify(decodeBase64Url(VECTOR_SIGNATURE)));
    }

    @Test
    void alteredPayloadFailsFixedVectorSignature() throws Exception {
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(loadRawPublicKey(decodeBase64Url(VECTOR_PUBLIC_KEY)));
        verifier.update(VECTOR_PAYLOAD.replace("\"counter\":1", "\"counter\":2")
                .getBytes(StandardCharsets.UTF_8));

        assertFalse(verifier.verify(decodeBase64Url(VECTOR_SIGNATURE)));
    }

    @Test
    void fixedVectorPublicKeyHashMatchesPayload() throws Exception {
        byte[] publicKey = decodeBase64Url(VECTOR_PUBLIC_KEY);
        String publicKeySha256 = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(publicKey));

        assertTrue(VECTOR_PAYLOAD.contains("\"publicKeySha256\":\"" + publicKeySha256 + "\""));
    }

    private static PublicKey loadRawPublicKey(byte[] rawKey) throws Exception {
        byte[] encoded = new byte[ED25519_X509_PREFIX.length + rawKey.length];
        System.arraycopy(ED25519_X509_PREFIX, 0, encoded, 0, ED25519_X509_PREFIX.length);
        System.arraycopy(rawKey, 0, encoded, ED25519_X509_PREFIX.length, rawKey.length);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
    }

    private static byte[] decodeBase64Url(String value) {
        int remainder = value.length() % 4;
        String padded = remainder == 0 ? value : value + "=".repeat(4 - remainder);
        return Base64.getUrlDecoder().decode(padded);
    }
}
