package source.auth.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import source.auth.model.enums.AdminAccessEventStatus;

@Entity
@Table(schema = "auth", name = "admin_access_events")
public class AdminAccessEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Column(name = "browser", length = 128)
    private String browser;

    @Column(name = "sanitized_user_agent", length = 512)
    private String sanitizedUserAgent;

    @Column(name = "ip_fingerprint", length = 64)
    private String ipFingerprint;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AdminAccessEventStatus status;

    @Column(name = "reason", length = 255)
    private String reason;

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public void setSanitizedUserAgent(String sanitizedUserAgent) {
        this.sanitizedUserAgent = sanitizedUserAgent;
    }

    public void setIpFingerprint(String ipFingerprint) {
        this.ipFingerprint = ipFingerprint;
    }

    public void setStatus(AdminAccessEventStatus status) {
        this.status = status;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
