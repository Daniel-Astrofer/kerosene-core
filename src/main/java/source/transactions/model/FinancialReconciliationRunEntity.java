package source.transactions.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "financial_reconciliation_runs", schema = "financial")
public class FinancialReconciliationRunEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "RUNNING";

    @Column(name = "checked_transfers", nullable = false)
    private int checkedTransfers = 0;

    @Column(name = "issue_count", nullable = false)
    private int issueCount = 0;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    public UUID getId() {
        return id;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCheckedTransfers() {
        return checkedTransfers;
    }

    public void setCheckedTransfers(int checkedTransfers) {
        this.checkedTransfers = checkedTransfers;
    }

    public int getIssueCount() {
        return issueCount;
    }

    public void setIssueCount(int issueCount) {
        this.issueCount = issueCount;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
