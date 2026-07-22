package source.sovereign.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import source.common.financial.FinancialQuorumPort;
import source.sovereign.quorum.FailStopPolicy;
import source.sovereign.quorum.QuorumMembership;
import source.sovereign.quorum.QuorumPeer;
import source.sovereign.quorum.QuorumPeerResult;
import source.sovereign.quorum.QuorumPhase;
import source.sovereign.quorum.QuorumPhaseResult;
import source.sovereign.quorum.QuorumTopology;
import source.sovereign.quorum.QuorumTransport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SovereignFinancialQuorumAdapterTest {

    @Mock
    private QuorumTransport transport;

    @Mock
    private QuorumMembership membership;

    @Mock
    private FailStopPolicy failStopPolicy;

    private SovereignFinancialQuorumAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SovereignFinancialQuorumAdapter(transport, membership, failStopPolicy, 2, false);
    }

    @Test
    void shouldAllowSingleLocalNodeWhenLocalSimulationEnabled() {
        SovereignFinancialQuorumAdapter localAdapter = new SovereignFinancialQuorumAdapter(
                transport,
                membership,
                failStopPolicy,
                2,
                true);
        when(membership.current()).thenReturn(new QuorumTopology(List.of()));

        FinancialQuorumPort.Result result = localAdapter.requireHealthyUnanimousConsensus("hash123");

        assertEquals(1, result.acceptedNodes());
        assertEquals(1, result.totalHealthyNodes());
        verify(failStopPolicy).recordQuorumSuccess();
        verifyNoInteractions(transport);
    }

    @Test
    void shouldThrowIfFewerThanMinimumHealthyNodes() {
        QuorumPeer peer1 = new QuorumPeer("https://host1");
        QuorumTopology topology = new QuorumTopology(List.of(peer1));

        when(membership.current()).thenReturn(topology);

        QuorumPeerResult peerResult = QuorumPeerResult.failed(peer1, "error");
        QuorumPhaseResult healthResult = new QuorumPhaseResult(
                QuorumPhase.HEALTH_CHECK,
                0,
                2,
                2,
                false,
                List.of(peerResult));
        when(transport.healthCheck(topology)).thenReturn(healthResult);

        assertThrows(IllegalStateException.class, () -> adapter.requireHealthyUnanimousConsensus("hash123"));
        verify(failStopPolicy).recordQuorumFailure(anyString());
    }

    @Test
    void shouldThrowIfPrepareNotUnanimous() {
        QuorumPeer peer1 = new QuorumPeer("https://host1");
        QuorumPeer peer2 = new QuorumPeer("https://host2");
        QuorumTopology topology = new QuorumTopology(List.of(peer1, peer2));

        when(membership.current()).thenReturn(topology);

        QuorumPhaseResult healthResult = new QuorumPhaseResult(
                QuorumPhase.HEALTH_CHECK,
                2,
                3,
                2,
                false,
                List.of(
                        QuorumPeerResult.accepted(peer1, 200),
                        QuorumPeerResult.accepted(peer2, 200)));
        when(transport.healthCheck(topology)).thenReturn(healthResult);

        QuorumPhaseResult prepareResult = new QuorumPhaseResult(
                QuorumPhase.PREPARE,
                1,
                3,
                2,
                false,
                List.of(
                        QuorumPeerResult.accepted(peer1, 200),
                        QuorumPeerResult.rejected(peer2, 400)));
        when(transport.prepare(eq("hash123"), any(QuorumTopology.class))).thenReturn(prepareResult);

        assertThrows(IllegalStateException.class, () -> adapter.requireHealthyUnanimousConsensus("hash123"));
        verify(failStopPolicy).recordQuorumFailure(anyString());
    }

    @Test
    void shouldReturnResultWhenCommitUnanimous() {
        QuorumPeer peer1 = new QuorumPeer("https://host1");
        QuorumPeer peer2 = new QuorumPeer("https://host2");
        QuorumTopology topology = new QuorumTopology(List.of(peer1, peer2));

        when(membership.current()).thenReturn(topology);

        QuorumPhaseResult healthResult = new QuorumPhaseResult(
                QuorumPhase.HEALTH_CHECK,
                2,
                3,
                2,
                false,
                List.of(
                        QuorumPeerResult.accepted(peer1, 200),
                        QuorumPeerResult.accepted(peer2, 200)));
        when(transport.healthCheck(topology)).thenReturn(healthResult);

        QuorumPhaseResult prepareResult = new QuorumPhaseResult(
                QuorumPhase.PREPARE,
                3,
                3,
                2,
                false,
                List.of(
                        QuorumPeerResult.accepted(peer1, 200),
                        QuorumPeerResult.accepted(peer2, 200)));
        when(transport.prepare(eq("hash123"), any(QuorumTopology.class))).thenReturn(prepareResult);

        QuorumPhaseResult commitResult = new QuorumPhaseResult(
                QuorumPhase.COMMIT,
                3,
                3,
                2,
                false,
                List.of(
                        QuorumPeerResult.accepted(peer1, 200),
                        QuorumPeerResult.accepted(peer2, 200)));
        when(transport.commit(eq("hash123"), any(QuorumTopology.class))).thenReturn(commitResult);

        FinancialQuorumPort.Result result = adapter.requireHealthyUnanimousConsensus("hash123");

        assertEquals(3, result.acceptedNodes());
        assertEquals(3, result.totalHealthyNodes());
        verify(failStopPolicy).recordQuorumSuccess();
    }
}
