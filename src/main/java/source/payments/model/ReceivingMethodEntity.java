package source.payments.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import source.security.persistence.StringCryptoConverter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "receiving_methods", schema = "financial", indexes = {
        @Index(name = "idx_receiving_methods_user_type_status", columnList = "user_id, type, status"),
        @Index(name = "idx_receiving_methods_public_ref", columnList = "public_reference_hash")
})
public class ReceivingMethodEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private PaymentEnums.ReceivingMethodType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaymentEnums.ReceivingMethodStatus status = PaymentEnums.ReceivingMethodStatus.ACTIVE;

    @Column(name = "label", length = 128)
    private String label;

    @Column(name = "priority", nullable = false)
    private Integer priority = 100;

    @Convert(converter = StringCryptoConverter.class)
    @Column(name = "metadata_encrypted", columnDefinition = "TEXT")
    private String metadataEncrypted;

    @Column(name = "public_reference_hash", length = 128)
    private String publicReferenceHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public PaymentEnums.ReceivingMethodType getType() {
        return type;
    }

    public void setType(PaymentEnums.ReceivingMethodType type) {
        this.type = type;
    }

    public PaymentEnums.ReceivingMethodStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentEnums.ReceivingMethodStatus status) {
        this.status = status;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getMetadataEncrypted() {
        return metadataEncrypted;
    }

    public void setMetadataEncrypted(String metadataEncrypted) {
        this.metadataEncrypted = metadataEncrypted;
    }

    public String getPublicReferenceHash() {
        return publicReferenceHash;
    }

    public void setPublicReferenceHash(String publicReferenceHash) {
        this.publicReferenceHash = publicReferenceHash;
    }
}
