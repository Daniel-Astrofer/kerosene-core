package source.auth.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import source.auth.model.enums.AdminAccessAttemptStatus;

@Entity
@Table(schema = "auth", name = "admin_access_attempts")
public class AdminAccessAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserDataBase user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_row_id", nullable = false)
    private AdminAccessDeviceEntity device;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AdminAccessAttemptStatus status = AdminAccessAttemptStatus.PENDING;

    @Column(name = "browser", length = 128)
    private String browser;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ip_fingerprint", length = 64)
    private String ipFingerprint;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public UUID getId() {
        return id;
    }

    public UserDataBase getUser() {
        return user;
    }

    public void setUser(UserDataBase user) {
        this.user = user;
    }

    public AdminAccessDeviceEntity getDevice() {
        return device;
    }

    public void setDevice(AdminAccessDeviceEntity device) {
        this.device = device;
    }

    public AdminAccessAttemptStatus getStatus() {
        return status;
    }

    public void setStatus(AdminAccessAttemptStatus status) {
        this.status = status;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getIpFingerprint() {
        return ipFingerprint;
    }

    public void setIpFingerprint(String ipFingerprint) {
        this.ipFingerprint = ipFingerprint;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }
}
