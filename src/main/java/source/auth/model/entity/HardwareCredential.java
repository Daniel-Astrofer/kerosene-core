package source.auth.model.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(schema = "auth", name = "hardware_credentials")
public class HardwareCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "public_key", nullable = false, unique = true)
    private String publicKey; // Base64 or Hex representation of the Ed25519 public key

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "signature_count", nullable = false)
    private long signatureCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserDataBase user;

    public HardwareCredential() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public long getSignatureCount() {
        return signatureCount;
    }

    public void setSignatureCount(long signatureCount) {
        this.signatureCount = signatureCount;
    }

    public UserDataBase getUser() {
        return user;
    }

    public void setUser(UserDataBase user) {
        this.user = user;
    }
}
