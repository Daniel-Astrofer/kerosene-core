package source.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VaultBootstrapCoordinatorTest {

    @Mock
    private VaultEndpointResolver endpointResolver;

    @Mock
    private VaultAttestationClient attestationClient;

    @Mock
    private VaultProvisioningClient provisioningClient;

    @Mock
    private MasterKeyMemoryStore masterKeyMemoryStore;

    private VaultBootstrapCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new VaultBootstrapCoordinator(
                endpointResolver,
                attestationClient,
                provisioningClient,
                masterKeyMemoryStore
        );
        ReflectionTestUtils.setField(coordinator, "proxyPath", "/tmp/proxy.sock");
        ReflectionTestUtils.setField(coordinator, "startupTimeoutMs", 1000L); // Fast timeout for tests
    }

    @Test
    void testStartDevelopmentMode() {
        ReflectionTestUtils.setField(coordinator, "vaultEnabled", false);
        ReflectionTestUtils.setField(coordinator, "devAesSecretBase64", "1234567890abcdef1234567890abcdef1234567890a=");
        
        assertDoesNotThrow(() -> coordinator.start());
        assertTrue(coordinator.isRunning());
        
        verify(masterKeyMemoryStore).storeMasterKey(any(byte[].class));
        verifyNoInteractions(attestationClient, provisioningClient);
    }

    @Test
    void testStartVaultModeSuccess() throws Exception {
        ReflectionTestUtils.setField(coordinator, "vaultEnabled", true);

        String vaultUrl = "http://localhost:8200";
        when(endpointResolver.resolveVaultUrl()).thenReturn(vaultUrl);

        VaultAttestationSession mockSession = mock(VaultAttestationSession.class);
        when(attestationClient.attest(vaultUrl)).thenReturn(mockSession);

        byte[] keyBytes = new byte[32];
        when(provisioningClient.provisionMasterKey(vaultUrl, mockSession)).thenReturn(keyBytes);

        assertDoesNotThrow(() -> coordinator.start());
        assertTrue(coordinator.isRunning());

        verify(masterKeyMemoryStore).storeMasterKey(any(byte[].class));
    }

    @Test
    void testStartVaultModeThrowsExceptionOnProxyPathBlank() {
        ReflectionTestUtils.setField(coordinator, "vaultEnabled", true);
        ReflectionTestUtils.setField(coordinator, "proxyPath", "");

        assertThrows(IllegalStateException.class, () -> coordinator.start());
        assertFalse(coordinator.isRunning());
    }

    @Test
    void testStartVaultModeTimeout() throws Exception {
        ReflectionTestUtils.setField(coordinator, "vaultEnabled", true);
        ReflectionTestUtils.setField(coordinator, "startupTimeoutMs", 50L);

        when(endpointResolver.resolveVaultUrl()).thenThrow(new RuntimeException("Network error"));

        assertThrows(IllegalStateException.class, () -> coordinator.start());
        assertFalse(coordinator.isRunning());
    }
}
