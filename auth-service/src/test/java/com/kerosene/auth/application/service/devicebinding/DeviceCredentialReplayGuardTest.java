package com.kerosene.auth.application.service.devicebinding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.kerosene.auth.application.service.cache.contracts.RedisServicer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceCredentialReplayGuardTest {

    @Mock
    private RedisServicer redisService;

    private DeviceCredentialReplayGuard guard;

    @BeforeEach
    void setUp() {
        guard = new DeviceCredentialReplayGuard(redisService, 3, 900L, 1200L);
    }

    @Test
    void thirdFailureActivatesSoftLock() {
        when(redisService.increment(anyString())).thenReturn(1L, 2L, 3L);

        assertFalse(guard.recordReplayFailure(9L, "cred-a", "PASSKEY"));
        assertFalse(guard.recordReplayFailure(9L, "cred-a", "PASSKEY"));
        assertTrue(guard.recordReplayFailure(9L, "cred-a", "PASSKEY"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisService).setValue(keyCaptor.capture(), eq("1"), eq(1200L));
        assertTrue(keyCaptor.getValue().contains("device_cred_replay_lock:9:"));
        verify(redisService).deleteValue(org.mockito.ArgumentMatchers.contains("device_cred_replay_fail:9:"));
    }

    @Test
    void isLockedReadsLockKey() {
        when(redisService.getValue(org.mockito.ArgumentMatchers.contains("device_cred_replay_lock:3:")))
                .thenReturn("1");
        assertTrue(guard.isLocked(3L, "ref"));
    }

    @Test
    void clearFailuresDeletesCounter() {
        guard.clearFailures(1L, "ref-x");
        verify(redisService).deleteValue("device_cred_replay_fail:1:ref-x");
        verify(redisService, never()).deleteValue(org.mockito.ArgumentMatchers.contains("lock:"));
    }

    @Test
    void firstFailureSetsWindowTtl() {
        when(redisService.increment(anyString())).thenReturn(1L);
        guard.recordReplayFailure(1L, "c", "DEVICE_KEY");
        verify(redisService).expire(anyString(), eq(900L));
    }
}
