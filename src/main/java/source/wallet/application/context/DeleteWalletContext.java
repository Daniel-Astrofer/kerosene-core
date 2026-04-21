package source.wallet.application.context;

import source.wallet.dto.WalletRequestDTO;
import source.wallet.model.WalletEntity;

public class DeleteWalletContext {

    private final Long userId;
    private final WalletRequestDTO request;
    private WalletEntity wallet;
    private String normalizedName;

    public DeleteWalletContext(Long userId, WalletRequestDTO request) {
        this.userId = userId;
        this.request = request;
    }

    public Long getUserId() {
        return userId;
    }

    public WalletRequestDTO getRequest() {
        return request;
    }

    public WalletEntity getWallet() {
        return wallet;
    }

    public void setWallet(WalletEntity wallet) {
        this.wallet = wallet;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }
}
