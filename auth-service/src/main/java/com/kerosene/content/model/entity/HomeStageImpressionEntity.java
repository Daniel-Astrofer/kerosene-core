package com.kerosene.content.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persisted acknowledgement of one home communication-stage content edition.
 * A user and fingerprint pair prevents a previously consumed ONCE stage from being shown again.
 */
@Entity
@Table(schema = "public", name = "home_stage_impression")
public class HomeStageImpressionEntity {

    /** Database identity assigned when this impression is inserted. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Account that acknowledged the stage; forms part of the impression lookup key. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Stage identifier retained for diagnostics and querying. */
    @Column(name = "stage_id", nullable = false, length = 128)
    private String stageId;

    /** Stable digest of stage identity and visible content used to detect a new edition. */
    @Column(name = "content_fingerprint", nullable = false, length = 64)
    private String contentFingerprint;

    /** Monotonic client acknowledgement state such as SEEN, READ, or DISMISSED. */
    @Column(nullable = false, length = 16)
    private String status = "READ";

    /** Time at which the current acknowledgement state was recorded. */
    @Column(name = "seen_at", nullable = false)
    private Instant seenAt;

    /** Optional expiry after which the acknowledgement no longer suppresses the stage. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Supplies creation defaults before the entity is first persisted. */
    @PrePersist
    void onCreate() {
        if (seenAt == null) {
            seenAt = Instant.now();
        }
        if (status == null || status.isBlank()) {
            status = "READ";
        }
    }

    /** Returns the generated database identity. */
    public Long getId() {
        return id;
    }

    /** Returns the account that acknowledged this stage. */
    public Long getUserId() {
        return userId;
    }

    /** Sets the account that owns this impression. */
    /** @param userId account identifier */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /** Returns the stage identifier associated with this acknowledgement. */
    public String getStageId() {
        return stageId;
    }

    /** Sets the stage identifier retained with this acknowledgement. */
    /** @param stageId stage identifier */
    public void setStageId(String stageId) {
        this.stageId = stageId;
    }

    /** Returns the content-edition fingerprint used for suppression. */
    public String getContentFingerprint() {
        return contentFingerprint;
    }

    /** Sets the stable fingerprint for the acknowledged stage edition. */
    /** @param contentFingerprint normalized stage content digest */
    public void setContentFingerprint(String contentFingerprint) {
        this.contentFingerprint = contentFingerprint;
    }

    /** Returns the current acknowledgement state. */
    public String getStatus() {
        return status;
    }

    /** Sets the acknowledgement state after monotonic status validation. */
    /** @param status acknowledgement state */
    public void setStatus(String status) {
        this.status = status;
    }

    /** Returns when the current acknowledgement state was recorded. */
    public Instant getSeenAt() {
        return seenAt;
    }

    /** Sets the timestamp for the current acknowledgement state. */
    /** @param seenAt acknowledgement timestamp */
    public void setSeenAt(Instant seenAt) {
        this.seenAt = seenAt;
    }

    /** Returns the optional expiry instant for this suppression record. */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** Sets the optional suppression expiry instant. */
    /** @param expiresAt expiry instant, or null for no expiry */
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
