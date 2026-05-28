package source.ledger.application.transaction;

import org.springframework.stereotype.Service;
import source.auth.AuthExceptions;
import source.auth.application.service.account.AccountActivationService;
import source.auth.application.service.user.UserService;
import source.auth.model.entity.UserDataBase;
import source.ledger.dto.TransactionDTO;
import source.ledger.exceptions.LedgerExceptions;
import source.wallet.application.port.in.WalletLookupPort;
import source.wallet.model.WalletEntity;

import java.util.List;

@Service
public class TransactionParticipantResolver {

    private final WalletLookupPort walletLookupPort;
    private final UserService userService;
    private final AccountActivationService accountActivationService;

    public TransactionParticipantResolver(
            WalletLookupPort walletLookupPort,
            UserService userService,
            AccountActivationService accountActivationService) {
        this.walletLookupPort = walletLookupPort;
        this.userService = userService;
        this.accountActivationService = accountActivationService;
    }

    public UserDataBase resolveAuthenticatedSender(Long senderUserId) {
        return userService.buscarPorId(senderUserId).orElseThrow(
                () -> new LedgerExceptions.LedgerNotFoundException("Sender (authenticated user) not found"));
    }

    public WalletEntity resolveSenderWallet(UserDataBase sender, String senderIdentifier) {
        List<WalletEntity> senderWallets = walletLookupPort.findByUserId(sender.getId());
        if (senderWallets == null || senderWallets.isEmpty()) {
            throw new LedgerExceptions.LedgerNotFoundException("Sender wallet not found");
        }

        if (senderIdentifier == null || senderIdentifier.trim().isEmpty()) {
            return walletLookupPort.requirePrimaryWallet(sender.getId());
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
            WalletEntity walletByAddress = walletLookupPort.findByDepositAddress(senderIdentifier);
            if (walletByAddress == null) {
                // Fallback to passphrase hash for partial compatibility or specific hash lookups
                walletByAddress = walletLookupPort.findByPassphraseHash(senderIdentifier);
            }

            if (walletByAddress != null && walletByAddress.getUser().getId().equals(sender.getId())) {
                return walletByAddress;
            }

            return senderWallets.stream()
                    .filter(wallet -> (wallet.getDepositAddress() != null && wallet.getDepositAddress().equals(senderIdentifier))
                            || wallet.getPassphraseHash().equals(senderIdentifier))
                    .findFirst()
                    .orElseThrow(() -> new LedgerExceptions.LedgerNotFoundException(
                            "Sender wallet with address '" + senderIdentifier + "' not found"));
        }

        // 4. Try as username
        if (senderIdentifier.equalsIgnoreCase(sender.getUsername())) {
            return walletLookupPort.requirePrimaryWallet(sender.getId());
        }

        // 5. Treat as wallet name
        WalletEntity walletByName = walletLookupPort.findByNameAndUserId(senderIdentifier, sender.getId());
        if (walletByName != null) {
            return walletByName;
        }

        throw new LedgerExceptions.LedgerNotFoundException(
                "Sender wallet with identifier '" + senderIdentifier + "' not found");
    }

    public WalletEntity resolveReceiverWallet(String receiverIdentifier) {
        if (receiverIdentifier == null || receiverIdentifier.trim().isEmpty()) {
            throw new LedgerExceptions.ReceiverNotFoundException("Receiver identifier cannot be empty");
        }

        if (TransactionDTO.isNumericId(receiverIdentifier)) {
            try {
                long walletId = Long.parseLong(receiverIdentifier);
                WalletEntity wallet = walletLookupPort.findById(walletId);
                if (wallet != null) {
                    return requireReadyReceiverWallet(wallet);
                }
            } catch (NumberFormatException ignored) {
                // Already guarded by isNumericId, kept defensive for malformed data.
            }
        }

        WalletEntity wallet = resolveReceiverByPublicIdentifier(receiverIdentifier);
        if (wallet != null) {
            return requireReadyReceiverWallet(wallet);
        }
        if (looksLikePublicWalletIdentifier(receiverIdentifier)) {
            throw new LedgerExceptions.ReceiverNotFoundException(
                    "Receiver wallet with identifier '" + receiverIdentifier + "' not found");
        }

        UserDataBase receiver = userService.findByUsername(receiverIdentifier);
        if (receiver == null) {
            throw new LedgerExceptions.ReceiverNotFoundException(
                    "Receiver username '" + receiverIdentifier + "' not found");
        }

        WalletEntity receiverWallet = walletLookupPort.findPrimaryWallet(receiver.getId());
        if (receiverWallet == null) {
            throw LedgerExceptions.ReceiverNotReadyException.noReceivingWallet();
        }

        assertReceiverInboundEnabled(receiver);
        return receiverWallet;
    }

    private WalletEntity resolveReceiverByPublicIdentifier(String receiverIdentifier) {
        if (TransactionDTO.isBitcoinAddress(receiverIdentifier)) {
            WalletEntity wallet = walletLookupPort.findByDepositAddress(receiverIdentifier);
            if (wallet != null) {
                return wallet;
            }
            return null;
        }

        if (looksLikeArgon2Hash(receiverIdentifier)) {
            WalletEntity wallet = walletLookupPort.findByPassphraseHash(receiverIdentifier);
            if (wallet != null) {
                return wallet;
            }
        }

        if (isDestinationHash(receiverIdentifier)) {
            return findWalletByDestinationHash(receiverIdentifier);
        }

        if (isHashFormat(receiverIdentifier)) {
            WalletEntity wallet = walletLookupPort.findByPassphraseHash(receiverIdentifier);
            if (wallet != null) {
                return wallet;
            }
        }

        return null;
    }

    private WalletEntity findWalletByDestinationHash(String receiverIdentifier) {
        return walletLookupPort.findByDestinationHash(receiverIdentifier);
    }

    private boolean looksLikeArgon2Hash(String identifier) {
        return identifier != null && identifier.startsWith("$argon2");
    }

    private WalletEntity requireReadyReceiverWallet(WalletEntity wallet) {
        assertReceiverInboundEnabled(wallet.getUser());
        return wallet;
    }

    private void assertReceiverInboundEnabled(UserDataBase receiver) {
        try {
            accountActivationService.assertInboundEnabled(receiver);
        } catch (AuthExceptions.InboundReceivingBlockedException ex) {
            throw LedgerExceptions.ReceiverNotReadyException.inboundBlocked();
        }
    }

    private boolean isDestinationHash(String identifier) {
        return identifier != null
                && !identifier.trim().isEmpty()
                && identifier.matches("^[A-Fa-f0-9]{64}$");
    }

    private boolean looksLikePublicWalletIdentifier(String identifier) {
        return TransactionDTO.isBitcoinAddress(identifier)
                || looksLikeArgon2Hash(identifier)
                || isDestinationHash(identifier)
                || isHashFormat(identifier);
    }

    private boolean isHashFormat(String identifier) {
        return identifier != null
                && !identifier.trim().isEmpty()
                && identifier.matches("^[A-Za-z0-9+/]+=*$")
                && identifier.contains("=");
    }
}
