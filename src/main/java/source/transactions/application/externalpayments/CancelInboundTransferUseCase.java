package source.transactions.application.externalpayments;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.exception.ExternalPaymentsExceptions;
import source.transactions.infra.CustodyGateway;
import source.transactions.model.ExternalTransferEntity;

import java.util.Locale;
import java.util.UUID;

@Service
public class CancelInboundTransferUseCase {

    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalTransferFactory externalTransferFactory;
    private final CustodyGateway custodyGateway;

    public CancelInboundTransferUseCase(
            ExternalTransfersPort externalTransfersPort,
            ExternalTransferFactory externalTransferFactory,
            CustodyGateway custodyGateway) {
        this.externalTransfersPort = externalTransfersPort;
        this.externalTransferFactory = externalTransferFactory;
        this.custodyGateway = custodyGateway;
    }

    @Transactional
    public ExternalTransferResponseDTO cancel(Long userId, UUID transferId) {
        ExternalTransferEntity transfer = externalTransfersPort.findByIdAndUserId(transferId, userId)
                .orElseThrow(() -> new ExternalPaymentsExceptions.TransferNotFound(
                        "The requested inbound transfer could not be found."));

        if (!isInboundTransfer(transfer)) {
            throw new ExternalPaymentsExceptions.TransferCancellationNotAllowed(
                    "Only inbound deposit flows can be cancelled.");
        }

        if ("CANCELLED".equalsIgnoreCase(transfer.getStatus())) {
            return externalTransferFactory.toResponseDTO(transfer);
        }

        if (!"PENDING".equalsIgnoreCase(transfer.getStatus())) {
            throw new ExternalPaymentsExceptions.TransferCancellationNotAllowed(
                    "Only pending inbound transfers can be cancelled.");
        }

        if ("INBOUND_INVOICE".equalsIgnoreCase(transfer.getTransferType())) {
            boolean cancelled = custodyGateway.cancelLightningInvoice(
                    new CustodyGateway.LightningInvoiceCancellationCommand(
                            transfer.getUserId(),
                            transfer.getWalletId(),
                            transfer.getWalletNameSnapshot(),
                            transfer.getExternalReference(),
                            transfer.getExternalReference(),
                            transfer.getInvoiceData()));
            if (!cancelled) {
                throw new ExternalPaymentsExceptions.TransferCancellationNotAllowed(
                        "The Lightning invoice could not be cancelled by the provider.");
            }
        }

        transfer.setStatus("CANCELLED");
        transfer.setContext(appendContext(transfer.getContext(), "Inbound deposit cancelled by user."));
        return externalTransferFactory.toResponseDTO(externalTransfersPort.save(transfer));
    }

    private boolean isInboundTransfer(ExternalTransferEntity transfer) {
        String type = transfer.getTransferType() != null
                ? transfer.getTransferType().toUpperCase(Locale.ROOT)
                : "";
        return "ADDRESS_ISSUE".equals(type)
                || "ONRAMP_PURCHASE".equals(type)
                || "INBOUND_INVOICE".equals(type);
    }

    private String appendContext(String currentContext, String suffix) {
        if (currentContext == null || currentContext.isBlank()) {
            return suffix;
        }
        if (currentContext.contains(suffix)) {
            return currentContext;
        }
        return currentContext + " | " + suffix;
    }
}
