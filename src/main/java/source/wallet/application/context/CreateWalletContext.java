package source.wallet.application.context;

import source.auth.model.entity.UserDataBase;
import source.wallet.dto.WalletRequestDTO;
import source.wallet.model.WalletEntity;
import source.wallet.model.WalletMode;

public class CreateWalletContext {

    private final Long userId;
    private final WalletRequestDTO request;
    private UserDataBase user;
    private String normalizedName;
    private String normalizedXpub;
    private WalletMode normalizedWalletMode = WalletMode.KEROSENE;
    private String totpSecret;
    private WalletEntity wallet;

    public CreateWalletContext(Long userId, WalletRequestDTO request) {
        this.userId = userId;
        this.request = request;
    }

    public Long getUserId() {
        return userId;
    }

    public WalletRequestDTO getRequest() {
        return request;
    }

    public UserDataBase getUser() {
        return user;
    }

    public void setUser(UserDataBase user) {
        this.user = user;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String getNormalizedXpub() {
        return normalizedXpub;
    }

    public void setNormalizedXpub(String normalizedXpub) {
        this.normalizedXpub = normalizedXpub;
    }

    public WalletMode getNormalizedWalletMode() {
        return normalizedWalletMode;
    }

    public void setNormalizedWalletMode(WalletMode normalizedWalletMode) {
        this.normalizedWalletMode = normalizedWalletMode != null ? normalizedWalletMode : WalletMode.KEROSENE;
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    public void setTotpSecret(String totpSecret) {
        this.totpSecret = totpSecret;
    }

    public WalletEntity getWallet() {
        return wallet;
    }

    public void setWallet(WalletEntity wallet) {
        this.wallet = wallet;
    }
}
