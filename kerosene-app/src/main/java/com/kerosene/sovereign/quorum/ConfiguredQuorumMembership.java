package source.sovereign.quorum;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class ConfiguredQuorumMembership implements QuorumMembership {

    private final String shardUrlsConfig;

    public ConfiguredQuorumMembership(@Value("${quorum.shard.urls:}") String shardUrlsConfig) {
        this.shardUrlsConfig = shardUrlsConfig;
    }

    @Override
    public QuorumTopology current() {
        List<QuorumPeer> peers = parsePeers();
        return new QuorumTopology(peers);
    }

    private List<QuorumPeer> parsePeers() {
        if (shardUrlsConfig == null || shardUrlsConfig.isBlank()) {
            return List.of();
        }

        return Arrays.stream(shardUrlsConfig.split(","))
                .map(String::trim)
                .filter(peer -> !peer.isEmpty())
                .map(QuorumPeer::new)
                .toList();
    }
}
