package source.auth.application.service.passkey;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import source.auth.application.service.cache.contracts.RedisServicer;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PasskeyServiceTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void verifySignatureAcceptsDynamicOnionHostWhenRequestMatchesOrigin() throws Exception {
        String onionHost = "epef24frbttdyirb45zif4smrkmhfd4di34my7wdhadzomfcpcf5fbyd.onion";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName(onionHost);
        request.addHeader("Host", onionHost);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        PasskeyService service = new PasskeyService(
                mock(RedisServicer.class),
                new ObjectMapper(),
                new ObjectMapper(),
                "http://localhost:3000,http://localhost:8080",
                "localhost");

        AssertionFixture assertion = createAssertion(
                onionHost,
                "http://" + onionHost,
                HexFormat.of().formatHex(new byte[32]));

        assertTrue(service.verifySignature(
                "alice",
                assertion.challengeHex(),
                assertion.signatureB64Url(),
                assertion.rawPublicKey(),
                assertion.authDataB64Url(),
                assertion.clientDataJsonB64Url()));
    }

    @Test
    void verifySignatureKeepsConfiguredRpIdForSubdomainOrigins() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("app.example.com");
        request.addHeader("Host", "app.example.com");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        PasskeyService service = new PasskeyService(
                mock(RedisServicer.class),
                new ObjectMapper(),
                new ObjectMapper(),
                "https://app.example.com",
                "example.com");

        AssertionFixture assertion = createAssertion(
                "example.com",
                "https://app.example.com",
                HexFormat.of().formatHex(new byte[] {
                        1, 1, 1, 1, 1, 1, 1, 1,
                        1, 1, 1, 1, 1, 1, 1, 1,
                        1, 1, 1, 1, 1, 1, 1, 1,
                        1, 1, 1, 1, 1, 1, 1, 1
                }));

        assertTrue(service.verifySignature(
                "alice",
                assertion.challengeHex(),
                assertion.signatureB64Url(),
                assertion.rawPublicKey(),
                assertion.authDataB64Url(),
                assertion.clientDataJsonB64Url()));
    }

    @Test
    void verifySignatureAcceptsConfiguredAndroidAppOriginWithOnionRpId() throws Exception {
        String onionHost = "epef24frbttdyirb45zif4smrkmhfd4di34my7wdhadzomfcpcf5fbyd.onion";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName(onionHost);
        request.addHeader("Host", onionHost);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        PasskeyService service = new PasskeyService(
                mock(RedisServicer.class),
                new ObjectMapper(),
                new ObjectMapper(),
                "android:apk-key-hash:kerosene,http://localhost:3000,http://localhost:8080",
                "localhost");

        AssertionFixture assertion = createAssertion(
                onionHost,
                "android:apk-key-hash:kerosene",
                HexFormat.of().formatHex(new byte[] {
                        2, 2, 2, 2, 2, 2, 2, 2,
                        2, 2, 2, 2, 2, 2, 2, 2,
                        2, 2, 2, 2, 2, 2, 2, 2,
                        2, 2, 2, 2, 2, 2, 2, 2
                }));

        assertTrue(service.isClientDataOriginAllowed(assertion.clientDataJsonB64Url()));
        assertTrue(service.verifySignature(
                "alice",
                assertion.challengeHex(),
                assertion.signatureB64Url(),
                assertion.rawPublicKey(),
                assertion.authDataB64Url(),
                assertion.clientDataJsonB64Url()));
    }

    @Test
    void authenticationVerificationRejectsRegistrationClientDataType() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("localhost");
        request.addHeader("Host", "localhost");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        PasskeyService service = new PasskeyService(
                mock(RedisServicer.class),
                new ObjectMapper(),
                new ObjectMapper(),
                "http://localhost:3000",
                "localhost");

        AssertionFixture assertion = createAssertion(
                "localhost",
                "http://localhost:3000",
                HexFormat.of().formatHex(new byte[] {
                        3, 3, 3, 3, 3, 3, 3, 3,
                        3, 3, 3, 3, 3, 3, 3, 3,
                        3, 3, 3, 3, 3, 3, 3, 3,
                        3, 3, 3, 3, 3, 3, 3, 3
                }),
                "webauthn.create");

        assertFalse(service.verifyAuthenticationSignature(
                "alice",
                assertion.challengeHex(),
                assertion.signatureB64Url(),
                assertion.rawPublicKey(),
                assertion.authDataB64Url(),
                assertion.clientDataJsonB64Url()));
    }

    @Test
    void emptyAllowedOriginsStillRequiresDynamicOriginHostToMatchRequestHost() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("localhost");
        request.addHeader("Host", "localhost");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        PasskeyService service = new PasskeyService(
                mock(RedisServicer.class),
                new ObjectMapper(),
                new ObjectMapper(),
                "",
                "localhost");

        AssertionFixture assertion = createAssertion(
                "localhost",
                "https://evil.example",
                HexFormat.of().formatHex(new byte[] {
                        4, 4, 4, 4, 4, 4, 4, 4,
                        4, 4, 4, 4, 4, 4, 4, 4,
                        4, 4, 4, 4, 4, 4, 4, 4,
                        4, 4, 4, 4, 4, 4, 4, 4
                }));

        assertFalse(service.isClientDataOriginAllowed(assertion.clientDataJsonB64Url()));
        assertFalse(service.verifyAuthenticationSignature(
                "alice",
                assertion.challengeHex(),
                assertion.signatureB64Url(),
                assertion.rawPublicKey(),
                assertion.authDataB64Url(),
                assertion.clientDataJsonB64Url()));
    }

    private AssertionFixture createAssertion(String rpId, String origin, String challengeHex) throws Exception {
        return createAssertion(rpId, origin, challengeHex, "webauthn.get");
    }

    private AssertionFixture createAssertion(String rpId, String origin, String challengeHex, String type) throws Exception {
        byte[] challengeBytes = HexFormat.of().parseHex(challengeHex);
        String challengeB64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
        byte[] clientDataBytes = ("""
                {"type":"%s","challenge":"%s","origin":"%s"}
                """.formatted(type, challengeB64Url, origin)).getBytes(StandardCharsets.UTF_8);

        byte[] authData = new byte[37];
        byte[] rpIdHash = MessageDigest.getInstance("SHA-256").digest(rpId.getBytes(StandardCharsets.UTF_8));
        System.arraycopy(rpIdHash, 0, authData, 0, rpIdHash.length);
        authData[32] = 0x05;
        authData[36] = 0x01;

        byte[] clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientDataBytes);
        byte[] signedData = new byte[authData.length + clientDataHash.length];
        System.arraycopy(authData, 0, signedData, 0, authData.length);
        System.arraycopy(clientDataHash, 0, signedData, authData.length, clientDataHash.length);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = generator.generateKeyPair();
        Signature signature = Signature.getInstance("EdDSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(signedData);

        return new AssertionFixture(
                challengeHex,
                Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign()),
                extractRawEd25519PublicKey(keyPair.getPublic().getEncoded()),
                Base64.getUrlEncoder().withoutPadding().encodeToString(authData),
                Base64.getUrlEncoder().withoutPadding().encodeToString(clientDataBytes));
    }

    private byte[] extractRawEd25519PublicKey(byte[] encodedPublicKey) {
        return Arrays.copyOfRange(encodedPublicKey, encodedPublicKey.length - 32, encodedPublicKey.length);
    }

    private record AssertionFixture(
            String challengeHex,
            String signatureB64Url,
            byte[] rawPublicKey,
            String authDataB64Url,
            String clientDataJsonB64Url) {
    }
}
