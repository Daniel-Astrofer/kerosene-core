package source.treasury.application.audit.handler;

import org.springframework.stereotype.Component;
import source.treasury.application.audit.AbstractFinancialAuditHandler;
import source.treasury.application.audit.FinancialAuditContext;
import source.treasury.application.port.in.CaptureReserveSnapshotUseCase;

@Component
public class CaptureReserveSnapshotHandler extends AbstractFinancialAuditHandler {

    private final CaptureReserveSnapshotUseCase captureReserveSnapshotUseCase;

    public CaptureReserveSnapshotHandler(CaptureReserveSnapshotUseCase captureReserveSnapshotUseCase) {
        this.captureReserveSnapshotUseCase = captureReserveSnapshotUseCase;
    }

    @Override
    protected void doHandle(FinancialAuditContext context) {
        context.setReserveSnapshot(captureReserveSnapshotUseCase.captureSnapshot());
        context.markExecuted();
    }
}
