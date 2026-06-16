package source.auth.model.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
        schema = "auth",
        name = "device_key_credentials",
        indexes = {
                @Index(name = "idx_device_key_user_id", columnList = "user_id"),
                @Index(name = "idx_device_key_install_id", columnList = "device_install_id"),
                @Index(name = "idx_device_key_status", columnList = "status")
        })
public class DeviceKeyCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserDataBase user;

    @Column(name = "credential_id", nullable = false, unique = true, length = 128)
    private String credentialId;

    @Column(name = "user_handle", nullable = false, length = 255)
    private String userHandle;

    @Column(name = "public_key_ed25519", nullable = false, length = 128)
    private String publicKeyEd25519;

    @Column(name = "algorithm", nullable = false, length = 32)
    private String algorithm = "Ed25519";

    @Column(name = "counter", nullable = false)
    private long counter;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "device_install_id", nullable = false, length = 128)
    private String deviceInstallId;

    @Column(name = "key_storage", length = 64)
    private String keyStorage;

    @Column(name = "platform", length = 128)
    private String platform;

    @Column(name = "browser", length = 128)
    private String browser;

    @Column(name = "onion_service_id", nullable = false, length = 255)
    private String onionServiceId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "protocol_version", nullable = false)
    private int protocolVersion = 1;

    @Column(name = "brand")
    private String brand;

    @Column(name = "model")
    private String model;

    @Column(name = "serial_number")
    private String serialNumber;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserDataBase getUser() {
        return user;
    }

    public void setUser(UserDataBase user) {
        this.user = user;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getUserHandle() {
        return userHandle;
    }

    public void setUserHandle(String userHandle) {
        this.userHandle = userHandle;
    }

    public String getPublicKeyEd25519() {
        return publicKeyEd25519;
    }

    public void setPublicKeyEd25519(String publicKeyEd25519) {
        this.publicKeyEd25519 = publicKeyEd25519;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public long getCounter() {
        return counter;
    }

    public void setCounter(long counter) {
        this.counter = counter;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceInstallId() {
        return deviceInstallId;
    }

    public void setDeviceInstallId(String deviceInstallId) {
        this.deviceInstallId = deviceInstallId;
    }

    public String getKeyStorage() {
        return keyStorage;
    }

    public void setKeyStorage(String keyStorage) {
        this.keyStorage = keyStorage;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getOnionServiceId() {
        return onionServiceId;
    }

    public void setOnionServiceId(String onionServiceId) {
        this.onionServiceId = onionServiceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null || status.isBlank()
                ? "ACTIVE"
                : status.trim().toUpperCase(Locale.ROOT);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }
}
