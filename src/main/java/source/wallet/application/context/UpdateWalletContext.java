package source.wallet.application.context;

import source.wallet.dto.WalletUpdateDTO;
import source.wallet.model.WalletEntity;
import source.wallet.model.WalletMode;

public class UpdateWalletContext {

    private final Long userId;
    private final WalletUpdateDTO request;
    private final boolean xpubChangeRequested;
    private final boolean walletModeChangeRequested;
    private WalletEntity wallet;
    private String normalizedCurrentName;
    private String normalizedNewName;
    private String normalizedXpub;
    private WalletMode normalizedWalletMode;

    public UpdateWalletContext(Long userId, WalletUpdateDTO request) {
        this.userId = userId;
        this.request = request;
        this.xpubChangeRequested = request.newXpub() != null;
        this.walletModeChangeRequested = request.newWalletMode() != null;
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

    public boolean isWalletModeChangeRequested() {
        return walletModeChangeRequested;
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

    public WalletMode getNormalizedWalletMode() {
        return normalizedWalletMode;
    }

    public void setNormalizedWalletMode(WalletMode normalizedWalletMode) {
        this.normalizedWalletMode = normalizedWalletMode;
    }
}
