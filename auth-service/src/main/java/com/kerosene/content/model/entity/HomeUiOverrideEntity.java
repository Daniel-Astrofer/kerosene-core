package com.kerosene.content.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persisted JSON patch targeting all users, a user, or a stable audience segment.
 * Priority and active dates control which patches participate in surface composition.
 */
@Entity
@Table(schema = "public", name = "home_ui_override")
public class HomeUiOverrideEntity {

    /** Database identity used to order overrides with equal priority. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Target scope discriminator interpreted by the override repository. */
    @Column(nullable = false, length = 16)
    private String scope;

    /** Optional account target for a user-specific override. */
    @Column(name = "user_id")
    private Long userId;

    /** Optional locale, balance, or bucket selector for segmented targeting. */
    @Column(name = "segment_key", length = 128)
    private String segmentKey;

    /** Overlay order; larger priorities are applied after smaller ones. */
    @Column(nullable = false)
    private int priority;

    /** Whether this override is eligible for matching and application. */
    @Column(nullable = false)
    private boolean active = true;

    /** Optional inclusive start instant for override eligibility. */
    @Column(name = "starts_at")
    private Instant startsAt;

    /** Optional end instant after which the override is inactive. */
    @Column(name = "ends_at")
    private Instant endsAt;

    /** JSON object patch merged into the composed surface. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** Creation timestamp initialized on first persistence. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Last modification timestamp maintained by the entity lifecycle. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Initializes creation and modification timestamps before initial persistence. */
    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    /** Refreshes the modification timestamp before each database update. */
    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Returns the generated database identity. */
    public Long getId() {
        return id;
    }

    /** Sets the database identity, primarily for persistence and fixture mapping. */
    /** @param id persisted identifier */
    public void setId(Long id) {
        this.id = id;
    }

    /** Returns the target-scope discriminator. */
    public String getScope() {
        return scope;
    }

    /** Sets the target-scope discriminator. */
    /** @param scope supported override scope */
    public void setScope(String scope) {
        this.scope = scope;
    }

    /** Returns the optional account target. */
    public Long getUserId() {
        return userId;
    }

    /** Sets the optional account target for user-scoped patches. */
    /** @param userId account identifier, or null for non-user targeting */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /** Returns the optional audience segment selector. */
    public String getSegmentKey() {
        return segmentKey;
    }

    /** Sets the optional locale, balance-mode, or bucket selector. */
    /** @param segmentKey segment expression used for audience matching */
    public void setSegmentKey(String segmentKey) {
        this.segmentKey = segmentKey;
    }

    /** Returns the numeric overlay priority. */
    public int getPriority() {
        return priority;
    }

    /** Sets the numeric overlay priority. */
    /** @param priority ordering value applied during patch composition */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /** Reports whether this row is enabled for matching. */
    public boolean isActive() {
        return active;
    }

    /** Enables or disables this override without deleting its definition. */
    /** @param active true when the row may be applied */
    public void setActive(boolean active) {
        this.active = active;
    }

    /** Returns the optional eligibility start instant. */
    public Instant getStartsAt() {
        return startsAt;
    }

    /** Sets the optional inclusive eligibility start instant. */
    /** @param startsAt start instant, or null for no lower bound */
    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    /** Returns the optional eligibility end instant. */
    public Instant getEndsAt() {
        return endsAt;
    }

    /** Sets the optional eligibility end instant. */
    /** @param endsAt end instant, or null for no upper bound */
    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    /** Returns the raw JSON patch payload. */
    public String getPayload() {
        return payload;
    }

    /** Sets the JSON object patch to merge into matching home surfaces. */
    /** @param payload serialized JSON object */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /** Returns when this override was first persisted. */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Sets the creation timestamp for persistence or migration use. */
    /** @param createdAt creation instant */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /** Returns when this override was last changed. */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Sets the last modification timestamp for persistence or migration use. */
    /** @param updatedAt modification instant */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
