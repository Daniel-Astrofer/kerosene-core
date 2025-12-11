package source.ledger.entity;

import jakarta.persistence.*;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;


@Entity
@Table(name = "ledger", schema = "financial")
public class LedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private WalletEntity wallet;

    @Column(name = "balance", precision = 38, scale = 16)
    private BigDecimal balance;

    @Column(name = "nonce", nullable = false)
    private Integer nonce;

    @Column(name = "last_hash", nullable = false, length = 256)
    private String lastHash;

    @Column(name = "context", nullable = false, length = 256)
    private String context;


    public LedgerEntity() {
        this.balance = BigDecimal.ZERO;
        this.nonce = 0;
    }

    public LedgerEntity(WalletEntity wallet, String context) {
        this();
        this.wallet = wallet;
        this.context = context;
    }


    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public WalletEntity getWallet() {
        return wallet;
    }

    public void setWallet(WalletEntity wallet) {
        this.wallet = wallet;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Integer getNonce() {
        return nonce;
    }

    public void setNonce(Integer nonce) {
        this.nonce = nonce;
    }

    public String getLastHash() {
        return lastHash;
    }

    public void setLastHash(String lastHash) {
        this.lastHash = lastHash;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    /**
     * Increments the nonce value by 1.
     */
    public void incrementNonce() {
        this.nonce++;
    }

    /**
     * Updates the balance by adding the specified amount.
     * 
     * @param amount the amount to add (can be negative for deductions)
     */
    public void updateBalance(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    @Override
    public String toString() {
        return "LedgerEntity{" +
                "id=" + id +
                ", walletId=" + (wallet != null ? wallet.getId() : null) +
                ", balance=" + balance +
                ", nonce=" + nonce +
                ", lastHash='" + lastHash + '\'' +
                ", context='" + context + '\'' +
                '}';
    }
}
