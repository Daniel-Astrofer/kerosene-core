package com.kerosene.auth.application.service.devicebinding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.kerosene.auth.application.infra.persistence.jpa.DeviceKeyCredentialRepository;
import com.kerosene.auth.application.infra.persistence.jpa.PasskeyCredentialRepository;
import com.kerosene.auth.model.entity.PasskeyCredential;
import com.kerosene.auth.model.entity.UserDataBase;

import java.util.List;

import com.kerosene.auth.dto.devicebinding.DeviceAlreadyBoundDTO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceBindingPolicyTest {

    @Mock
    private PasskeyCredentialRepository passkeyCredentialRepository;
    @Mock
    private DeviceKeyCredentialRepository deviceKeyCredentialRepository;

    private DeviceBindingPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new DeviceBindingPolicy(passkeyCredentialRepository, deviceKeyCredentialRepository);
    }

    @Test
    void freeDeviceDoesNothing() {
        when(passkeyCredentialRepository.findActiveByDeviceInstallId("install-1"))
                .thenReturn(List.of());
        when(deviceKeyCredentialRepository.findActiveByDeviceInstallId("install-1"))
                .thenReturn(List.of());

        assertNull(policy.ensureDeviceAvailableForBind("install-1", null, false));

        verify(passkeyCredentialRepository, never()).deleteByDeviceInstallId(eq("install-1"));
    }

    @Test
    void occupiedDeviceWithoutConfirmReturnsConflictPayload() {
        PasskeyCredential credential = activePasskey(10L, "alice", "install-1");
        when(passkeyCredentialRepository.findActiveByDeviceInstallId("install-1"))
                .thenReturn(List.of(credential));

        DeviceAlreadyBoundDTO conflict = policy.ensureDeviceAvailableForBind("install-1", null, false);

        assertNotNull(conflict);
        assertEquals("al***e", conflict.previousUsernameMasked());
        assertEquals(DeviceAlreadyBoundDTO.ACTION_CONFIRM_UNLINK, conflict.action());
        verify(passkeyCredentialRepository, never()).deleteByDeviceInstallId(eq("install-1"));
    }

    @Test
    void occupiedDeviceWithConfirmDeletesCredentials() {
        PasskeyCredential credential = activePasskey(10L, "alice", "install-1");
        when(passkeyCredentialRepository.findActiveByDeviceInstallId("install-1"))
                .thenReturn(List.of(credential));
        when(passkeyCredentialRepository.deleteByDeviceInstallId("install-1")).thenReturn(1);
        when(deviceKeyCredentialRepository.deleteByDeviceInstallId("install-1")).thenReturn(0);

        assertNull(policy.ensureDeviceAvailableForBind("install-1", null, true));

        verify(passkeyCredentialRepository).deleteByDeviceInstallId("install-1");
        verify(deviceKeyCredentialRepository).deleteByDeviceInstallId("install-1");
    }

    @Test
    void sameUserRebindReplacesOwnCredentialsOnly() {
        PasskeyCredential credential = activePasskey(10L, "alice", "install-1");
        when(passkeyCredentialRepository.findActiveByDeviceInstallId("install-1"))
                .thenReturn(List.of(credential));
        when(deviceKeyCredentialRepository.findActiveByDeviceInstallId("install-1"))
                .thenReturn(List.of());

        assertNull(policy.ensureDeviceAvailableForBind("install-1", 10L, false));

        verify(passkeyCredentialRepository).deleteByUserIdAndDeviceInstallId(10L, "install-1");
        verify(deviceKeyCredentialRepository).deleteByUserIdAndDeviceInstallId(10L, "install-1");
        verify(passkeyCredentialRepository, never()).deleteByDeviceInstallId(eq("install-1"));
    }

    @Test
    void maskUsernameKeepsReadableHint() {
        assertEquals("al***e", DeviceBindingPolicy.maskUsername("alice"));
        assertEquals("a***", DeviceBindingPolicy.maskUsername("ab"));
    }

    private static PasskeyCredential activePasskey(long userId, String username, String installId) {
        UserDataBase owner = mock(UserDataBase.class);
        when(owner.getId()).thenReturn(userId);
        when(owner.getUsername()).thenReturn(username);
        PasskeyCredential credential = new PasskeyCredential();
        credential.setUser(owner);
        credential.setStatus("ACTIVE");
        credential.setDeviceInstallId(installId);
        return credential;
    }
}
