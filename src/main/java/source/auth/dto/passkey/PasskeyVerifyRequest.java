package source.auth.dto.passkey;

public class PasskeyVerifyRequest {
    private String username;
    private String signature;
    private String authData;
    private String clientDataJSON;
    private String credentialId;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public String getAuthData() { return authData; }
    public void setAuthData(String authData) { this.authData = authData; }
    public String getClientDataJSON() { return clientDataJSON; }
    public void setClientDataJSON(String clientDataJSON) { this.clientDataJSON = clientDataJSON; }
    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
}
