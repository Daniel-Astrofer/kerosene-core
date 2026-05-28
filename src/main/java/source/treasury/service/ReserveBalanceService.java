package source.treasury.service;

import org.springframework.stereotype.Service;
import source.treasury.application.port.in.CaptureReserveSnapshotUseCase;
import source.treasury.domain.model.ReserveSnapshot;

@Service
public class ReserveBalanceService {

    private final CaptureReserveSnapshotUseCase captureReserveSnapshotUseCase;

    public ReserveBalanceService(CaptureReserveSnapshotUseCase captureReserveSnapshotUseCase) {
        this.captureReserveSnapshotUseCase = captureReserveSnapshotUseCase;
    }

    public ReserveSnapshot captureSnapshot() {
        return captureReserveSnapshotUseCase.captureSnapshot();
    }
}
