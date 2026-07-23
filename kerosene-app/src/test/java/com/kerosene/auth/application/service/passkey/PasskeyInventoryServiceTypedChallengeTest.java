package com.kerosene.auth.application.service.passkey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.kerosene.auth.application.infra.persistence.jpa.DeviceKeyCredentialRepository;
import com.kerosene.auth.application.infra.persistence.jpa.PasskeyCredentialRepository;
import com.kerosene.auth.application.service.devicekey.DeviceKeyService;
import com.kerosene.auth.dto.PasskeyActionRequiredDTO;
import com.kerosene.auth.dto.devicekey.DeviceKeyChallengeResponse;
import com.kerosene.auth.model.entity.UserDataBase;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasskeyInventoryServiceTypedChallengeTest {

    @Mock
    private PasskeyCredentialRepository passkeyCredentialRepository;
    @Mock
    private DeviceKeyCredentialRepository deviceKeyCredentialRepository;
    @Mock
    private PasskeyService passkeyService;
    @Mock
    private DeviceKeyService deviceKeyService;

    private PasskeyInventoryService service;

    @BeforeEach
    void setUp() {
        service = new PasskeyInventoryService(
                passkeyCredentialRepository,
                deviceKeyCredentialRepository,
                passkeyService,
                deviceKeyService,
                90L,
                true);
    }

    @Test
    void buildChallengeRequired_emitsDeviceKeyAndPasskeyWhenBothAvailable() {
        UserDataBase user = user(42L, "alice");
        when(passkeyCredentialRepository.findInventoryByUserId(42L)).thenReturn(List.of());
        when(passkeyService.resolveCurrentRelyingPartyId()).thenReturn("kerosene-device");
        when(passkeyService.resolveCurrentRequestHost()).thenReturn("localhost");
        when(deviceKeyCredentialRepository.existsActiveByUserId(42L)).thenReturn(true);
        when(deviceKeyService.startAuthenticationChallenge(any(UserDataBase.class)))
                .thenReturn(new DeviceKeyChallengeResponse(
                        "chal-id",
                        "dk-hex",
                        90L,
                        "onion",
                        "Ed25519",
                        "KEROSENE_JSON_V1"));

        PasskeyActionRequiredDTO dto = service.buildChallengeRequired(
                user,
                "passkey-hex-challenge",
                "step-up required");

        assertEquals("ASSERT_PASSKEY", dto.action());
        assertEquals("passkey-hex-challenge", dto.challenge());
        assertNotNull(dto.acceptedFactors());
        assertTrue(dto.acceptedFactors().contains("DEVICE_KEY"));
        assertTrue(dto.acceptedFactors().contains("PASSKEY"));
        assertEquals("DEVICE_KEY", dto.preferredFactor());
        assertNotNull(dto.challenges());
        assertEquals("chal-id", dto.challenges().get("DEVICE_KEY").challengeId());
        assertEquals("dk-hex", dto.challenges().get("DEVICE_KEY").challenge());
        assertEquals("passkey-hex-challenge", dto.challenges().get("PASSKEY").challenge());
    }

    @Test
    void buildChallengeRequired_passkeyOnlyWhenNoDeviceKey() {
        UserDataBase user = user(7L, "bob");
        when(passkeyCredentialRepository.findInventoryByUserId(7L)).thenReturn(List.of());
        when(passkeyService.resolveCurrentRelyingPartyId()).thenReturn("kerosene-device");
        when(passkeyService.resolveCurrentRequestHost()).thenReturn("localhost");
        when(deviceKeyCredentialRepository.existsActiveByUserId(7L)).thenReturn(false);

        PasskeyActionRequiredDTO dto = service.buildChallengeRequired(
                user,
                "only-passkey",
                "need passkey");

        assertEquals(List.of("PASSKEY"), dto.acceptedFactors());
        assertEquals("PASSKEY", dto.preferredFactor());
        assertEquals("only-passkey", dto.challenge());
        assertTrue(dto.challenges().containsKey("PASSKEY"));
        assertTrue(!dto.challenges().containsKey("DEVICE_KEY"));
    }

    private static UserDataBase user(long id, String username) {
        UserDataBase user = mock(UserDataBase.class);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn(username);
        when(user.hasTotpEnabled()).thenReturn(false);
        return user;
    }
}
