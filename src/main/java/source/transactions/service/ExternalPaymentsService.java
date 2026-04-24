package source.transactions.service;

import org.springframework.stereotype.Service;
import source.transactions.application.externalpayments.CreateLightningInvoiceUseCase;
import source.transactions.application.externalpayments.CancelInboundTransferUseCase;
import source.transactions.application.externalpayments.ExternalPaymentsQueryService;
import source.transactions.application.externalpayments.IssueOnchainAddressUseCase;
import source.transactions.application.externalpayments.PayLightningPaymentUseCase;
import source.transactions.application.externalpayments.SendOnchainPaymentUseCase;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.dto.LightningInvoiceRequestDTO;
import source.transactions.dto.LightningInvoiceResponseDTO;
import source.transactions.dto.LightningPaymentRequestDTO;
import source.transactions.dto.OnchainAddressAllocationDTO;
import source.transactions.dto.OnchainAddressRequestDTO;
import source.transactions.dto.OnchainSendRequestDTO;
import source.transactions.dto.WalletNetworkAddressDTO;

import java.util.List;
import java.util.UUID;

@Service
public class ExternalPaymentsService {

    private final IssueOnchainAddressUseCase issueOnchainAddressUseCase;
    private final CreateLightningInvoiceUseCase createLightningInvoiceUseCase;
    private final CancelInboundTransferUseCase cancelInboundTransferUseCase;
    private final SendOnchainPaymentUseCase sendOnchainPaymentUseCase;
    private final PayLightningPaymentUseCase payLightningPaymentUseCase;
    private final ExternalPaymentsQueryService externalPaymentsQueryService;

    public ExternalPaymentsService(
            IssueOnchainAddressUseCase issueOnchainAddressUseCase,
            CreateLightningInvoiceUseCase createLightningInvoiceUseCase,
            CancelInboundTransferUseCase cancelInboundTransferUseCase,
            SendOnchainPaymentUseCase sendOnchainPaymentUseCase,
            PayLightningPaymentUseCase payLightningPaymentUseCase,
            ExternalPaymentsQueryService externalPaymentsQueryService) {
        this.issueOnchainAddressUseCase = issueOnchainAddressUseCase;
        this.createLightningInvoiceUseCase = createLightningInvoiceUseCase;
        this.cancelInboundTransferUseCase = cancelInboundTransferUseCase;
        this.sendOnchainPaymentUseCase = sendOnchainPaymentUseCase;
        this.payLightningPaymentUseCase = payLightningPaymentUseCase;
        this.externalPaymentsQueryService = externalPaymentsQueryService;
    }

    public OnchainAddressAllocationDTO issueOnchainAddress(Long userId, OnchainAddressRequestDTO request) {
        return issueOnchainAddressUseCase.issue(userId, request);
    }

    public WalletNetworkAddressDTO getWalletNetworkProfile(Long userId, String walletName) {
        return externalPaymentsQueryService.getWalletNetworkProfile(userId, walletName);
    }

    public LightningInvoiceResponseDTO createLightningInvoice(Long userId, LightningInvoiceRequestDTO request) {
        return createLightningInvoiceUseCase.create(userId, request);
    }

    public ExternalTransferResponseDTO cancelInboundTransfer(Long userId, UUID transferId) {
        return cancelInboundTransferUseCase.cancel(userId, transferId);
    }

    public ExternalTransferResponseDTO sendOnchain(Long userId, OnchainSendRequestDTO request) {
        return sendOnchainPaymentUseCase.send(userId, request);
    }

    public ExternalTransferResponseDTO payLightning(Long userId, LightningPaymentRequestDTO request) {
        return payLightningPaymentUseCase.pay(userId, request);
    }

    public List<ExternalTransferResponseDTO> listTransfers(Long userId) {
        return externalPaymentsQueryService.listTransfers(userId);
    }

    public ExternalTransferResponseDTO getTransfer(Long userId, UUID transferId) {
        return externalPaymentsQueryService.getTransfer(userId, transferId);
    }
}
