package source.sovereign.quorum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🛰️ RAFT-LIGHT (SMR: State Machine Replication)
 * ─────────────────────────────────────────────────────────────
 * Protocolo de replicação de log para evitar Drift de Estado entre Shards.
 * Garante que os Shards sigam a mesma ordem de transações e recalculem
 * o Merkle Root de forma determinística.
 */
@Service
public class RaftLogService {

    private static final Logger log = LoggerFactory.getLogger(RaftLogService.class);

    // Log Local de Operações (Index -> Operation)
    private final ConcurrentHashMap<Long, String> operationLog = new ConcurrentHashMap<>();
    private long lastAppliedIndex = 0;

    /**
     * Propõe uma operação ao log.
     * Em produção, isso passaria pela fase de PREPARE e COMMIT do quórum antes de ser aplicado.
     */
    public synchronized long appendToLog(String operation) {
        long nextIndex = lastAppliedIndex + 1;
        operationLog.put(nextIndex, operation);
        lastAppliedIndex = nextIndex;

        log.info("[RaftLog] Entry #{} appended: {}", nextIndex, operation);
        return nextIndex;
    }

    /**
     * Sincroniza logs ausentes de outros shards.
     * Resolve o problema de "Drift" quando um shard volta de uma offline temporária.
     */
    public synchronized void syncFromPeers(List<LogEntry> missingEntries) {
        for (LogEntry entry : missingEntries) {
            if (entry.index > lastAppliedIndex) {
                operationLog.put(entry.index, entry.operation);
                lastAppliedIndex = entry.index;
                log.info("[RaftLog] Replayed missing entry #{}", entry.index);
            }
        }
    }

    public long getLastAppliedIndex() {
        return lastAppliedIndex;
    }

    public static record LogEntry(long index, String operation) {}
}
