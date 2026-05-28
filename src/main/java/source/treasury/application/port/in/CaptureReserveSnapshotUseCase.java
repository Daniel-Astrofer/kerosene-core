package source.treasury.application.port.in;

import source.treasury.domain.model.ReserveSnapshot;

public interface CaptureReserveSnapshotUseCase {

    ReserveSnapshot captureSnapshot();
}
