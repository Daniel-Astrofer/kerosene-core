package source.ledger.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import source.security.SuicideService;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SIMULAÇÃO DE CORTE DE SHARD
 * Verifica se o quórum de 2/3 é respeitado.
 *
 * Topologia:
 * 1. Shard Alpha (IS) - Local
 * 2. Shard Singapore (SG) - Remoto
 * 3. Shard Switzerland (CH) - Remoto
 */
public class QuorumSyncSimulationTest {

    private QuorumSyncService quorumSyncService;
    private TorMtlsService torMtlsService;
    private SuicideService suicideService;

    @BeforeEach
    void setUp() {
        torMtlsService = mock(TorMtlsService.class);
        suicideService = mock(SuicideService.class);

        quorumSyncService = new QuorumSyncService(suicideService, torMtlsService);

        // Simular configuração de URLs dos peers
        ReflectionTestUtils.setField(quorumSyncService, "shardUrlsConfig",
            "https://shard-sg.kerosene.onion,https://shard-ch.kerosene.onion");
    }

    @Test
    void testQuorumFailsWhenSGIsDown() throws IOException {
        // Shard Alpha (IS) - Local ( implicit 1 ACK )

        // Simular SG DERRUBADO (Singapore Down) - Lança IOException ou retorna erro
        when(torMtlsService.post(contains("shard-sg"), anyString(), anyString()))
            .thenThrow(new IOException("SOCKS5 connection failed: Host unreachable (SG DOWN)"));

        // Simular CH DERRUBADO TAMBÉM? Não, vamos simular que CH está de pé.
        when(torMtlsService.post(contains("shard-ch"), anyString(), anyString()))
            .thenReturn(new TorMtlsService.QuorumResponse(HttpStatus.OK.value()));

        // Tentar propor transação
        // Shard Alpha(1) + Shard CH(1) = 2 ACKs.
        // QUORUM_REQUIRED é 2. Deve PASSAR com 2/3?
        // Wait, TOTAL_SHARDS = 3. QUORUM_REQUIRED = (3/2)+1 = 2.

        boolean result = quorumSyncService.proposeTransactionToQuorum("TEST_HASH_123");

        assertTrue(result, "Quórum deveria passar com 2/3 (Alpha + Switzerland)");
    }

    @Test
    void testQuorumFailsWhenSGAndCHAreDown() throws IOException {
        // Simular SG DERRUBADO
        when(torMtlsService.post(contains("shard-sg"), anyString(), anyString()))
            .thenThrow(new IOException("SG DOWN"));

        // Simular CH DERRUBADO
        when(torMtlsService.post(contains("shard-ch"), anyString(), anyString()))
            .thenThrow(new IOException("CH DOWN"));

        // Alpha(1) + 0 remotos = 1 ACK. 1 < 2. Deve FALHAR.
        boolean result = quorumSyncService.proposeTransactionToQuorum("TEST_HASH_456");

        assertFalse(result, "Quórum DEVE FALHAR com apenas 1/3 dos nós (Alpha isolado)");
    }
}
