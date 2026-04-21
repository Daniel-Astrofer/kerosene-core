package source.ledger.application.transaction;

import org.springframework.stereotype.Service;
import source.auth.application.service.user.UserService;
import source.auth.model.entity.UserDataBase;
import source.ledger.dto.TransactionDTO;
import source.ledger.exceptions.LedgerExceptions;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletContract;

import java.util.List;

@Service
public class TransactionParticipantResolver {

    private final WalletContract walletService;
    private final UserService userService;

    public TransactionParticipantResolver(WalletContract walletService, UserService userService) {
        this.walletService = walletService;
        this.userService = userService;
    }

    public UserDataBase resolveAuthenticatedSender(Long senderUserId) {
        return userService.buscarPorId(senderUserId).orElseThrow(
                () -> new LedgerExceptions.LedgerNotFoundException("Sender (authenticated user) not found"));
    }

    public WalletEntity resolveSenderWallet(UserDataBase sender, String senderIdentifier) {
        List<WalletEntity> senderWallets = walletService.findByUserId(sender.getId());
        if (senderWallets == null || senderWallets.isEmpty()) {
            throw new LedgerExceptions.LedgerNotFoundException("Sender wallet not found");
        }

        if (senderIdentifier == null || senderIdentifier.trim().isEmpty()) {
            return senderWallets.get(0);
        }

        if (TransactionDTO.isNumericId(senderIdentifier)) {
            long walletId = Long.parseLong(senderIdentifier);
            return senderWallets.stream()
                    .filter(wallet -> wallet.getId() == walletId)
                    .findFirst()
                    .orElseThrow(() -> new LedgerExceptions.LedgerNotFoundException(
                            "Sender wallet with ID " + walletId + " not found"));
        }

        if (TransactionDTO.isBitcoinAddress(senderIdentifier) || isHashFormat(senderIdentifier)) {
            WalletEntity walletByAddress = walletService.findByPassphraseHash(senderIdentifier);
            if (walletByAddress != null && walletByAddress.getUser().getId().equals(sender.getId())) {
                return walletByAddress;
            }

            return senderWallets.stream()
                    .filter(wallet -> wallet.getPassphraseHash().equals(senderIdentifier))
                    .findFirst()
                    .orElseThrow(() -> new LedgerExceptions.LedgerNotFoundException(
                            "Sender wallet with address '" + senderIdentifier + "' not found"));
        }

        String normalizedIdentifier = senderIdentifier.toUpperCase();
        return senderWallets.stream()
                .filter(wallet -> wallet.getName().equals(normalizedIdentifier))
                .findFirst()
                .orElseThrow(() -> new LedgerExceptions.LedgerNotFoundException(
                        "Sender wallet with name '" + senderIdentifier + "' not found"));
    }

    public WalletEntity resolveReceiverWallet(String receiverIdentifier) {
        if (receiverIdentifier == null || receiverIdentifier.trim().isEmpty()) {
            throw new LedgerExceptions.ReceiverNotFoundException("Receiver identifier cannot be empty");
        }

        if (TransactionDTO.isNumericId(receiverIdentifier)) {
            try {
                long walletId = Long.parseLong(receiverIdentifier);
                WalletEntity wallet = walletService.findById(walletId);
                if (wallet != null) {
                    return wallet;
                }
            } catch (NumberFormatException ignored) {
                // Already guarded by isNumericId, kept defensive for malformed data.
            }
        }

        if (TransactionDTO.isBitcoinAddress(receiverIdentifier) || isHashFormat(receiverIdentifier)) {
            WalletEntity wallet = walletService.findByPassphraseHash(receiverIdentifier);
            if (wallet != null) {
                return wallet;
            }

            throw new LedgerExceptions.ReceiverNotFoundException(
                    "Receiver wallet with address '" + receiverIdentifier + "' not found");
        }

        UserDataBase receiver = userService.findByUsername(receiverIdentifier);
        if (receiver == null) {
            throw new LedgerExceptions.ReceiverNotFoundException(
                    "Receiver username '" + receiverIdentifier + "' not found");
        }

        List<WalletEntity> receiverWallets = walletService.findByUserId(receiver.getId());
        if (receiverWallets == null || receiverWallets.isEmpty()) {
            throw new LedgerExceptions.ReceiverNotFoundException(
                    "Receiver wallet not found for user '" + receiverIdentifier + "'");
        }

        return receiverWallets.get(0);
    }

    private boolean isHashFormat(String identifier) {
        return identifier != null
                && !identifier.trim().isEmpty()
                && identifier.matches("^[A-Za-z0-9+/]+=*$")
                && identifier.contains("=");
    }
}
