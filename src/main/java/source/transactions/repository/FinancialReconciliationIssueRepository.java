package source.transactions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.transactions.model.FinancialReconciliationIssueEntity;

import java.util.UUID;

@Repository
public interface FinancialReconciliationIssueRepository extends JpaRepository<FinancialReconciliationIssueEntity, UUID> {
}
