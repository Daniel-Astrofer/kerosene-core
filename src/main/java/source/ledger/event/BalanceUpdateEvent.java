package source.ledger.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BalanceUpdateEvent {
    private Long walletId;
    private String walletName;
    private Long userId;
    private BigDecimal newBalance;
    private BigDecimal amount;
    private String context;
    private LocalDateTime timestamp;

    public BalanceUpdateEvent(Long walletId, String walletName, Long userId, BigDecimal newBalance,
            BigDecimal amount, String context) {
        this.walletId = walletId;
        this.walletName = walletName;
        this.userId = userId;
        this.newBalance = newBalance;
        this.amount = amount;
        this.context = context;
        this.timestamp = LocalDateTime.now();
    }

    // Getters
    public Long getWalletId() {
        return walletId;
    }

    public String getWalletName() {
        return walletName;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getNewBalance() {
        return newBalance;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getContext() {
        return context;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    // Setters
    public void setWalletId(Long walletId) {
        this.walletId = walletId;
    }

    public void setWalletName(String walletName) {
        this.walletName = walletName;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setNewBalance(BigDecimal newBalance) {
        this.newBalance = newBalance;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
