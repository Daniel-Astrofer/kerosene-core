package source.notification.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;

@Entity
@Table(schema = "public", name = "notifications")
public class NotificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private String kind;
    private String severity;
    private String title;
    private String body;
    private String deeplink;
    @Column(name = "entity_type")
    private String entityType;
    @Column(name = "entity_id")
    private String entityId;
    @Column(name = "is_read")
    private boolean read;
    /** Stored as UTC wall clock; wire format is Instant with explicit {@code Z}. */
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);

    /** Structured fields for clients (rail, amountBtc, confirmations, …). */
    @Convert(converter = StringStringMapJsonConverter.class)
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, String> metadata = Collections.emptyMap();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getDeeplink() { return deeplink; }
    public void setDeeplink(String deeplink) { this.deeplink = deeplink; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    @JsonIgnore
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** REST/WS clients: Zulu Instant (e.g. {@code 2026-07-21T22:47:00Z}). */
    @JsonProperty("createdAt")
    public Instant getCreatedAtUtc() {
        return createdAt == null ? null : createdAt.toInstant(ZoneOffset.UTC);
    }

    @JsonProperty("timestamp")
    public Instant getTimestampUtc() {
        return getCreatedAtUtc();
    }

    public Map<String, String> getMetadata() {
        return metadata == null ? Collections.emptyMap() : metadata;
    }
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata == null || metadata.isEmpty()
                ? Collections.emptyMap()
                : Map.copyOf(metadata);
    }
}
