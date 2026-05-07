package source.transactions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.transactions.model.FinancialReconciliationRunEntity;

import java.util.UUID;

@Repository
public interface FinancialReconciliationRunRepository extends JpaRepository<FinancialReconciliationRunEntity, UUID> {
}
