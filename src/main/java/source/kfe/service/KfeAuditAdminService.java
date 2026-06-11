package source.kfe.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.kfe.dto.KfeAuditEventResponse;
import source.kfe.dto.KfeAuditLatestResponse;
import source.kfe.dto.KfeAuditRootResponse;
import source.kfe.model.KfeAuditLogEntity;
import source.kfe.repository.KfeAuditLogRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class KfeAuditAdminService {

    private static final String EMPTY_ROOT = "0".repeat(64);

    private final KfeAuditLogRepository repository;
    private final KfeHashService hashService;

    public KfeAuditAdminService(KfeAuditLogRepository repository, KfeHashService hashService) {
        this.repository = repository;
        this.hashService = hashService;
    }

    @Transactional(readOnly = true)
    public KfeAuditLatestResponse latest() {
        KfeAuditEventResponse latest = repository.findTopByOrderBySequenceNumberDesc()
                .map(this::toResponse)
                .orElse(null);
        return new KfeAuditLatestResponse(latest, root());
    }

    @Transactional(readOnly = true)
    public List<KfeAuditEventResponse> events(int limit) {
        int safeLimit = Math.max(1, Math.min(500, limit));
        return repository.findAllByOrderBySequenceNumberDesc(PageRequest.of(0, safeLimit))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<KfeAuditEventResponse> transactionEvents(UUID transactionId) {
        return repository.findByTransactionIdOrderBySequenceNumberAsc(transactionId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public KfeAuditRootResponse root() {
        List<KfeAuditLogEntity> events = repository.findAllByOrderBySequenceNumberAsc();
        if (events.isEmpty()) {
            return new KfeAuditRootResponse(EMPTY_ROOT, 0L, null, null, LocalDateTime.now());
        }

        List<String> level = events.stream()
                .map(KfeAuditLogEntity::getEventHash)
                .toList();
        while (level.size() > 1) {
            level = nextLevel(level);
        }
        return new KfeAuditRootResponse(
                level.getFirst(),
                events.size(),
                events.getFirst().getSequenceNumber(),
                events.getLast().getSequenceNumber(),
                LocalDateTime.now());
    }

    private List<String> nextLevel(List<String> level) {
        java.util.ArrayList<String> next = new java.util.ArrayList<>();
        for (int index = 0; index < level.size(); index += 2) {
            String left = level.get(index);
            String right = index + 1 < level.size() ? level.get(index + 1) : left;
            next.add(hashService.sha256(left + "|" + right));
        }
        return next;
    }

    private KfeAuditEventResponse toResponse(KfeAuditLogEntity event) {
        return new KfeAuditEventResponse(
                event.getSequenceNumber(),
                event.getId(),
                event.getTransactionId(),
                event.getWalletId(),
                event.getEventType(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getPayloadHash(),
                event.getPreviousHash(),
                event.getEventHash(),
                event.getCreatedAt());
    }
}
