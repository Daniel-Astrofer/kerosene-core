package source.ledger.application.transaction;

import org.springframework.stereotype.Service;
import source.auth.application.service.account.AccountActivationService;
import source.auth.application.service.user.UserService;
import source.auth.model.entity.UserDataBase;
import source.common.service.AddressDerivationService;
import source.ledger.application.paymentrequest.PaymentRequestDestinationHashService;
import source.ledger.dto.TransactionDTO;
import source.ledger.exceptions.LedgerExceptions;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletContract;

import java.util.List;
import java.util.Locale;

@Service
public class TransactionParticipantResolver {

    private final WalletContract walletService;
    private final UserService userService;
    private final AccountActivationService accountActivationService;
    private final PaymentRequestDestinationHashService destinationHashService;
    private final AddressDerivationService addressDerivationService;

    public TransactionParticipantResolver(
            WalletContract walletService,
            UserService userService,
            AccountActivationService accountActivationService,
            PaymentRequestDestinationHashService destinationHashService,
            AddressDerivationService addressDerivationService) {
        this.walletService = walletService;
        this.userService = userService;
        this.accountActivationService = accountActivationService;
        this.destinationHashService = destinationHashService;
        this.addressDerivationService = addressDerivationService;
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
            WalletEntity walletByAddress = walletService.findByDepositAddress(senderIdentifier);
            if (walletByAddress == null) {
                // Fallback to passphrase hash for partial compatibility or specific hash lookups
                walletByAddress = walletService.findByPassphraseHash(senderIdentifier);
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
            List<WalletEntity> wallets = walletService.findByUserId(sender.getId());
            if (!wallets.isEmpty()) {
                return wallets.get(0);
            }
        }

        // 5. Treat as wallet name
        WalletEntity walletByName = walletService.findByNameAndUserId(senderIdentifier, sender.getId());
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
                WalletEntity wallet = walletService.findById(walletId);
                if (wallet != null) {
                    accountActivationService.assertInboundEnabled(wallet.getUser());
                    return wallet;
                }
            } catch (NumberFormatException ignored) {
                // Already guarded by isNumericId, kept defensive for malformed data.
            }
        }

        WalletEntity wallet = resolveReceiverByPublicIdentifier(receiverIdentifier);
        if (wallet != null) {
            accountActivationService.assertInboundEnabled(wallet.getUser());
            return wallet;
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
        accountActivationService.assertInboundEnabled(receiver);

        List<WalletEntity> receiverWallets = walletService.findByUserId(receiver.getId());
        if (receiverWallets == null || receiverWallets.isEmpty()) {
            throw new LedgerExceptions.ReceiverNotFoundException(
                    "Receiver wallet not found for user '" + receiverIdentifier + "'");
        }

        return receiverWallets.get(0);
    }

    private WalletEntity resolveReceiverByPublicIdentifier(String receiverIdentifier) {
        if (TransactionDTO.isBitcoinAddress(receiverIdentifier)) {
            WalletEntity wallet = walletService.findByDepositAddress(receiverIdentifier);
            if (wallet != null) {
                return wallet;
            }
            return findWalletByDerivedBlockchainAddress(receiverIdentifier);
        }

        if (looksLikeArgon2Hash(receiverIdentifier)) {
            WalletEntity wallet = walletService.findByPassphraseHash(receiverIdentifier);
            if (wallet != null) {
                return wallet;
            }
        }

        if (isDestinationHash(receiverIdentifier)) {
            return findWalletByDestinationHash(receiverIdentifier);
        }

        if (isHashFormat(receiverIdentifier)) {
            WalletEntity wallet = walletService.findByPassphraseHash(receiverIdentifier);
            if (wallet != null) {
                return wallet;
            }
        }

        return null;
    }

    private WalletEntity findWalletByDerivedBlockchainAddress(String receiverIdentifier) {
        for (WalletEntity wallet : walletService.findAll()) {
            if (wallet == null || wallet.getId() == null || !canDeriveStaticBlockchainAddress(wallet)) {
                continue;
            }

            String derivedAddress = addressDerivationService.deriveAddress(wallet.getId(), wallet.getPassphraseHash());
            if (receiverIdentifier.equals(derivedAddress)) {
                return wallet;
            }
        }
        return null;
    }

    private WalletEntity findWalletByDestinationHash(String receiverIdentifier) {
        String normalizedIdentifier = receiverIdentifier.toLowerCase(Locale.ROOT);
        for (WalletEntity wallet : walletService.findAll()) {
            if (wallet == null) {
                continue;
            }

            String destinationHash = destinationHashService.buildDestinationHash(wallet);
            if (destinationHash != null && destinationHash.equalsIgnoreCase(normalizedIdentifier)) {
                return wallet;
            }
        }
        return null;
    }

    private boolean canDeriveStaticBlockchainAddress(WalletEntity wallet) {
        return wallet.getPassphraseHash() != null
                && !wallet.getPassphraseHash().isBlank()
                && (wallet.getDepositAddress() == null || wallet.getDepositAddress().isBlank())
                && (wallet.getXpub() == null || wallet.getXpub().isBlank());
    }

    private boolean looksLikeArgon2Hash(String identifier) {
        return identifier != null && identifier.startsWith("$argon2");
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
