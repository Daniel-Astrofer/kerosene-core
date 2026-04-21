package source.wallet.application.context;

import source.wallet.dto.WalletUpdateDTO;
import source.wallet.model.WalletEntity;

public class UpdateWalletContext {

    private final Long userId;
    private final WalletUpdateDTO request;
    private final boolean xpubChangeRequested;
    private WalletEntity wallet;
    private String normalizedCurrentName;
    private String normalizedNewName;
    private String normalizedXpub;

    public UpdateWalletContext(Long userId, WalletUpdateDTO request) {
        this.userId = userId;
        this.request = request;
        this.xpubChangeRequested = request.newXpub() != null;
    }

    public Long getUserId() {
        return userId;
    }

    public WalletUpdateDTO getRequest() {
        return request;
    }

    public boolean isXpubChangeRequested() {
        return xpubChangeRequested;
    }

    public WalletEntity getWallet() {
        return wallet;
    }

    public void setWallet(WalletEntity wallet) {
        this.wallet = wallet;
    }

    public String getNormalizedCurrentName() {
        return normalizedCurrentName;
    }

    public void setNormalizedCurrentName(String normalizedCurrentName) {
        this.normalizedCurrentName = normalizedCurrentName;
    }

    public String getNormalizedNewName() {
        return normalizedNewName;
    }

    public void setNormalizedNewName(String normalizedNewName) {
        this.normalizedNewName = normalizedNewName;
    }

    public String getNormalizedXpub() {
        return normalizedXpub;
    }

    public void setNormalizedXpub(String normalizedXpub) {
        this.normalizedXpub = normalizedXpub;
    }
}
