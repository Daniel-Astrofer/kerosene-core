package source.auth.dto;


import source.auth.dto.contracts.UserDTOContract;
import com.fasterxml.jackson.annotation.JsonInclude;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO implements UserDTOContract {

    private String username;
    private String passphrase;
    private String totpSecret;
    private String totpCode;
    private String ip;
    private String deviceHash;

    public String getDeviceHash() {
        return deviceHash;
    }

    public void setDeviceHash(String deviceHash) {
        this.deviceHash = deviceHash;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassphrase() {
        return passphrase;
    }

    @Override
    public String getTotpSecret() {
        return totpSecret;
    }

    @Override
    public String getTotpCode() {
        return totpCode;
    }

    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    @Override
    public void setTotpSecret(String totpSecret) {
        this.totpSecret = totpSecret;
    }

    @Override
    public void setTotpCode(String totpCode) {
        this.totpCode = totpCode;
    }
}
