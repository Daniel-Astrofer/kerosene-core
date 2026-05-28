package source.ledger.application.transaction;

import source.auth.model.entity.UserDataBase;
import source.ledger.dto.TransactionDTO;
import source.wallet.model.WalletEntity;

public class TransactionContext {

    private final TransactionDTO transaction;
    private Long senderUserId;
    private UserDataBase sender;
    private WalletEntity senderWallet;
    private WalletEntity receiverWallet;
    private String effectiveContext;

    public TransactionContext(TransactionDTO transaction) {
        this.transaction = transaction;
    }

    public TransactionDTO getTransaction() {
        return transaction;
    }

    public Long getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(Long senderUserId) {
        this.senderUserId = senderUserId;
    }

    public UserDataBase getSender() {
        return sender;
    }

    public void setSender(UserDataBase sender) {
        this.sender = sender;
    }

    public WalletEntity getSenderWallet() {
        return senderWallet;
    }

    public void setSenderWallet(WalletEntity senderWallet) {
        this.senderWallet = senderWallet;
    }

    public WalletEntity getReceiverWallet() {
        return receiverWallet;
    }

    public void setReceiverWallet(WalletEntity receiverWallet) {
        this.receiverWallet = receiverWallet;
    }

    public String getEffectiveContext() {
        return effectiveContext;
    }

    public void setEffectiveContext(String effectiveContext) {
        this.effectiveContext = effectiveContext;
    }
}
