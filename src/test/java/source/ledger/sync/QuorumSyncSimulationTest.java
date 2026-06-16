package source.sovereign.quorum;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import source.security.SuicideService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuorumSyncSimulationTest {

    private static final QuorumTopology THREE_NODE_TOPOLOGY = new QuorumTopology(List.of(
            new QuorumPeer("https://shard-sg.kerosene.onion"),
            new QuorumPeer("https://shard-ch.kerosene.onion")));

    private QuorumSyncService quorumSyncService;
    private QuorumTransport transport;
    private QuorumMembership membership;
    private FailStopPolicy failStopPolicy;
    private SuicideService suicideService;

    @BeforeEach
    void setUp() {
        transport = mock(QuorumTransport.class);
        membership = mock(QuorumMembership.class);
        suicideService = mock(SuicideService.class);
        failStopPolicy = new DefaultFailStopPolicy(suicideService, 30_000, true);

        when(membership.current()).thenReturn(THREE_NODE_TOPOLOGY);
        quorumSyncService = new QuorumSyncService(transport, membership, failStopPolicy);
    }

    @Test
    void shouldCommitWhenOneRemoteShardIsDownButQuorumIsAvailable() {
        when(transport.prepare("TEST_HASH_123", THREE_NODE_TOPOLOGY))
                .thenReturn(result(QuorumPhase.PREPARE, 2, false));
        when(transport.commit("TEST_HASH_123", THREE_NODE_TOPOLOGY))
                .thenReturn(result(QuorumPhase.COMMIT, 2, false));

        boolean result = quorumSyncService.proposeTransactionToQuorum("TEST_HASH_123");

        assertTrue(result, "Quorum should pass with 2/3 nodes.");
    }

    @Test
    void shouldRejectWhenBothRemoteShardsAreDown() {
        when(transport.prepare("TEST_HASH_456", THREE_NODE_TOPOLOGY))
                .thenReturn(result(QuorumPhase.PREPARE, 1, false));

        boolean result = quorumSyncService.proposeTransactionToQuorum("TEST_HASH_456");

        assertFalse(result, "Quorum must fail with only 1/3 nodes.");
    }

    @Test
    void shouldEnterFailStopWhenPrepareTimesOut() {
        when(transport.prepare("TEST_HASH_TIMEOUT", THREE_NODE_TOPOLOGY))
                .thenReturn(result(QuorumPhase.PREPARE, 2, true));

        boolean result = quorumSyncService.proposeTransactionToQuorum("TEST_HASH_TIMEOUT");

        assertFalse(result);
        assertTrue(quorumSyncService.isFailStopMode());
        verify(suicideService).triggerInstantSuicide(contains("Phase 1 quorum timeout"));
    }

    @Test
    void shouldClearFailStopOnlyAfterHealthQuorumIsAvailable() {
        when(transport.prepare("TEST_HASH_TIMEOUT", THREE_NODE_TOPOLOGY))
                .thenReturn(result(QuorumPhase.PREPARE, 2, true));
        quorumSyncService.proposeTransactionToQuorum("TEST_HASH_TIMEOUT");

        when(transport.healthCheck(THREE_NODE_TOPOLOGY))
                .thenReturn(result(QuorumPhase.HEALTH_CHECK, 2, false));

        quorumSyncService.exitFailStopMode();

        assertFalse(quorumSyncService.isFailStopMode());
    }

    @Test
    void shouldKeepFailStopWhenHealthQuorumIsUnavailable() {
        when(transport.prepare("TEST_HASH_TIMEOUT", THREE_NODE_TOPOLOGY))
                .thenReturn(result(QuorumPhase.PREPARE, 2, true));
        quorumSyncService.proposeTransactionToQuorum("TEST_HASH_TIMEOUT");

        when(transport.healthCheck(THREE_NODE_TOPOLOGY))
                .thenReturn(result(QuorumPhase.HEALTH_CHECK, 1, false));

        assertThrows(SplitBrainException.class, quorumSyncService::exitFailStopMode);
        assertTrue(quorumSyncService.isFailStopMode());
    }

    private QuorumPhaseResult result(QuorumPhase phase, int acceptedNodes, boolean timedOut) {
        return new QuorumPhaseResult(
                phase,
                acceptedNodes,
                THREE_NODE_TOPOLOGY.totalNodes(),
                THREE_NODE_TOPOLOGY.requiredQuorum(),
                timedOut,
                List.of());
    }
}
