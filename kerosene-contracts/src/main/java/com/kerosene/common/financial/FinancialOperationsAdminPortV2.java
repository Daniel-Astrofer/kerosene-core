package com.kerosene.common.financial;

import java.time.Instant;
import java.util.List;

public interface FinancialOperationsAdminPortV2 {

    BlockchainStatus blockchain();
    LightningStatus lightning();
    PaginatedLogs logs(LogsRequest request);
    SystemMetrics metrics();

    record BlockchainStatus(
        String network,
        int blockHeight,
        int peerCount,
        boolean synced,
        Instant lastBlockTime,
        String version
    ) {
        public BlockchainStatus {
            if (network == null || network.isBlank()) throw new IllegalArgumentException("network required");
            if (blockHeight < 0) throw new IllegalArgumentException("blockHeight must be >= 0");
            if (peerCount < 0) throw new IllegalArgumentException("peerCount must be >= 0");
        }
    }

    record LightningStatus(
        String network,
        int activeChannels,
        int pendingChannels,
        long totalCapacitySats,
        boolean syncedToChain,
        String version
    ) {
        public LightningStatus {
            if (network == null || network.isBlank()) throw new IllegalArgumentException("network required");
            if (activeChannels < 0) throw new IllegalArgumentException("activeChannels must be >= 0");
            if (pendingChannels < 0) throw new IllegalArgumentException("pendingChannels must be >= 0");
            if (totalCapacitySats < 0) throw new IllegalArgumentException("totalCapacitySats must be >= 0");
        }
    }

    record LogEntry(
        Instant timestamp,
        String level,       // INFO, WARN, ERROR
        String source,      // class or component
        String message,     // redacted — never raw secrets
        String correlationId
    ) {
        public LogEntry {
            if (level == null || level.isBlank()) throw new IllegalArgumentException("level required");
            if (message == null || message.isBlank()) throw new IllegalArgumentException("message required");
            if (timestamp == null) throw new IllegalArgumentException("timestamp required");
        }
    }

    record PaginatedLogs(
        List<LogEntry> entries,
        int total,
        int page,
        int pageSize
    ) {
        public PaginatedLogs {
            if (entries == null) entries = List.of();
            if (page < 0) throw new IllegalArgumentException("page must be >= 0");
            if (pageSize < 1) throw new IllegalArgumentException("pageSize must be >= 1");
        }
    }

    record LogsRequest(
        int limit,
        String level,           // optional filter
        String correlationId    // optional filter
    ) {
        public LogsRequest {
            if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be 1-1000");
        }
    }

    record SystemMetrics(
        long heapUsedBytes,
        long heapMaxBytes,
        double cpuLoad,
        int activeThreads,
        int openFileDescriptors,
        long uptimeSeconds
    ) {
        public SystemMetrics {
            if (heapUsedBytes < 0) throw new IllegalArgumentException("heapUsedBytes must be >= 0");
            if (cpuLoad < 0) throw new IllegalArgumentException("cpuLoad must be >= 0");
            if (activeThreads < 0) throw new IllegalArgumentException("activeThreads must be >= 0");
        }
    }
}
