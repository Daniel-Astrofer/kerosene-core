package source.payments.service;

import org.springframework.stereotype.Service;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.model.entity.UserDataBase;
import source.payments.dto.ReceivingCapabilitiesResponse;
import source.payments.model.PaymentEnums;
import source.payments.repository.ReceivingMethodRepository;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ReceivingCapabilityService {

    private static final long MIN_INTERNAL_SATS = 1L;
    private static final long MIN_LIGHTNING_SATS = 1L;
    private static final long MIN_ONCHAIN_SATS = 546L;
    private static final ReceivingCapabilitiesResponse.Limits DEFAULT_LIMITS =
            new ReceivingCapabilitiesResponse.Limits(
                    "BTC",
                    List.of("BRL"),
                    MIN_INTERNAL_SATS,
                    MIN_LIGHTNING_SATS,
                    MIN_ONCHAIN_SATS);

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final ReceivingMethodRepository receivingMethodRepository;

    public ReceivingCapabilityService(
            UserRepository userRepository,
            WalletRepository walletRepository,
            ReceivingMethodRepository receivingMethodRepository) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.receivingMethodRepository = receivingMethodRepository;
    }

    public ReceivingCapabilitiesResponse capabilities(String receiverIdentifier) {
        Optional<UserDataBase> receiver = resolveUser(receiverIdentifier);
        if (receiver.isEmpty() || !isActive(receiver.get())) {
            return new ReceivingCapabilitiesResponse(
                    false,
                    false,
                    false,
                    null,
                    List.of("RECEIVER_NOT_READY"),
                    null,
                    List.of(),
                    DEFAULT_LIMITS);
        }

        Long receiverUserId = receiver.get().getId();
        List<WalletEntity> wallets = receiverWallets(receiverUserId);
        boolean internal = true;
        boolean lightning = hasReceivingMethod(receiverUserId, PaymentEnums.ReceivingMethodType.LIGHTNING)
                || wallets.stream().anyMatch(wallet -> hasText(wallet.getLightningAddress()));
        boolean onchain = hasReceivingMethod(receiverUserId, PaymentEnums.ReceivingMethodType.ONCHAIN)
                || wallets.stream()
                        .anyMatch(wallet -> hasText(wallet.getDepositAddress()) || hasText(wallet.getXpub()));

        List<String> missing = new ArrayList<>();
        if (!lightning) {
            missing.add("LIGHTNING_RECEIVER_METHOD_NOT_FOUND");
        }
        if (!onchain) {
            missing.add("ONCHAIN_METHOD_NOT_FOUND");
        }

        return new ReceivingCapabilitiesResponse(
                internal,
                lightning,
                onchain,
                preferredRail(internal, lightning, onchain),
                missing,
                displayName(receiver.get()),
                availableRails(internal, lightning, onchain),
                DEFAULT_LIMITS);
    }

    public Optional<UserDataBase> resolveUser(String receiverIdentifier) {
        if (!hasText(receiverIdentifier)) {
            return Optional.empty();
        }
        String normalized = receiverIdentifier.trim();
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        if (normalized.matches("\\d+")) {
            return userRepository.findById(Long.parseLong(normalized));
        }
        return Optional.ofNullable(userRepository.findByUsername(normalized.toLowerCase(Locale.ROOT)));
    }

    public boolean canReceiveLightning(Long userId) {
        return hasReceivingMethod(userId, PaymentEnums.ReceivingMethodType.LIGHTNING)
                || receiverWallets(userId).stream().anyMatch(wallet -> hasText(wallet.getLightningAddress()));
    }

    public boolean canReceiveOnchain(Long userId) {
        return hasReceivingMethod(userId, PaymentEnums.ReceivingMethodType.ONCHAIN)
                || receiverWallets(userId).stream()
                        .anyMatch(wallet -> hasText(wallet.getDepositAddress()) || hasText(wallet.getXpub()));
    }

    public boolean isActive(UserDataBase user) {
        return user != null && Boolean.TRUE.equals(user.getIsActive());
    }

    private boolean hasReceivingMethod(Long userId, PaymentEnums.ReceivingMethodType type) {
        return receivingMethodRepository.findFirstByUserIdAndTypeAndStatusOrderByPriorityAsc(
                userId,
                type,
                PaymentEnums.ReceivingMethodStatus.ACTIVE).isPresent();
    }

    private List<WalletEntity> receiverWallets(Long receiverUserId) {
        return walletRepository.findByUserId(receiverUserId).stream()
                .filter(wallet -> Boolean.TRUE.equals(wallet.getIsActive()))
                .toList();
    }

    private PaymentEnums.PaymentRail preferredRail(boolean internal, boolean lightning, boolean onchain) {
        if (internal) {
            return PaymentEnums.PaymentRail.INTERNAL;
        }
        if (lightning) {
            return PaymentEnums.PaymentRail.LIGHTNING;
        }
        if (onchain) {
            return PaymentEnums.PaymentRail.ONCHAIN;
        }
        return null;
    }

    private List<PaymentEnums.PaymentRail> availableRails(boolean internal, boolean lightning, boolean onchain) {
        List<PaymentEnums.PaymentRail> rails = new ArrayList<>();
        if (internal) {
            rails.add(PaymentEnums.PaymentRail.INTERNAL);
        }
        if (lightning) {
            rails.add(PaymentEnums.PaymentRail.LIGHTNING);
        }
        if (onchain) {
            rails.add(PaymentEnums.PaymentRail.ONCHAIN);
        }
        return List.copyOf(rails);
    }

    private String displayName(UserDataBase user) {
        return "@" + user.getUsername();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
