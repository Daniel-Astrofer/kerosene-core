package source.auth.dto;

import source.transactions.dto.PaymentLinkDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountActivationStatusDTO(
        boolean activated,
        boolean canReceiveInbound,
        boolean requiresActivationDeposit,
        BigDecimal requiredAmountBtc,
        String paymentLinkId,
        String depositAddress,
        String paymentStatus,
        String warningMessage,
        LocalDateTime activatedAt) {

    public static final String INBOUND_BLOCKED_MESSAGE =
            "Para receber fundos dentro da plataforma, deposite algum valor primeiro.";

    public static AccountActivationStatusDTO from(UserDataBaseView user, PaymentLinkDTO link) {
        return new AccountActivationStatusDTO(
                user.isActive(),
                user.isActive(),
                !user.isActive(),
                BigDecimal.ZERO,
                link != null ? link.getId() : null,
                link != null ? link.getDepositAddress() : null,
                link != null ? link.getStatus() : null,
                user.isActive()
                        ? null
                        : INBOUND_BLOCKED_MESSAGE,
                user.activatedAt());
    }

    public interface UserDataBaseView {
        boolean isActive();

        LocalDateTime activatedAt();
    }
}
