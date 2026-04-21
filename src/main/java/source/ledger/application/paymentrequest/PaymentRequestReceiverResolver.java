package source.ledger.application.paymentrequest;

import org.springframework.stereotype.Service;
import source.ledger.dto.InternalPaymentRequestDTO;
import source.ledger.exceptions.LedgerExceptions;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletContract;

@Service
public class PaymentRequestReceiverResolver {

    private final WalletContract walletService;
    private final PaymentRequestDestinationHashService destinationHashService;

    public PaymentRequestReceiverResolver(
            WalletContract walletService,
            PaymentRequestDestinationHashService destinationHashService) {
        this.walletService = walletService;
        this.destinationHashService = destinationHashService;
    }

    public WalletEntity resolveLockedReceiverWallet(InternalPaymentRequestDTO request) {
        WalletEntity wallet = null;
        if (request.getReceiverWalletId() != null) {
            wallet = walletService.findById(request.getReceiverWalletId());
        }

        if (wallet == null && request.getReceiverWalletName() != null && request.getRequesterUserId() != null) {
            wallet = walletService.findByNameAndUserId(request.getReceiverWalletName(), request.getRequesterUserId());
        }

        if (wallet == null) {
            throw new LedgerExceptions.ReceiverNotFoundException(
                    "Receiver wallet locked in this payment request was not found.");
        }

        if (!wallet.getUser().getId().equals(request.getRequesterUserId())) {
            throw new LedgerExceptions.ReceiverNotFoundException(
                    "Receiver wallet locked in this payment request no longer belongs to the requester.");
        }

        if (request.getReceiverWalletId() == null) {
            request.setReceiverWalletId(wallet.getId());
        }
        if (request.getDestinationHash() == null || request.getDestinationHash().isBlank()) {
            request.setDestinationHash(destinationHashService.buildDestinationHash(wallet));
        }

        return wallet;
    }
}
