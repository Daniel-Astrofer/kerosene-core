package source.ledger.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "siphon_requests", schema = "financial")
public class SiphonRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @Column(nullable = false)
    private LocalDateTime executableAfter;

    @Column(nullable = false)
    private String status; // PENDING, EXECUTED, CANCELLED

    public SiphonRequest() {}

    public SiphonRequest(BigDecimal amount) {
        this.amount = amount;
        this.requestedAt = LocalDateTime.now();
        this.executableAfter = this.requestedAt.plusHours(24);
        this.status = "PENDING";
    }

    public UUID getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getExecutableAfter() { return executableAfter; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
