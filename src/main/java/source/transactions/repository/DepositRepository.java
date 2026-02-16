package source.transactions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.transactions.model.DepositEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepositRepository extends JpaRepository<DepositEntity, Long> {

    Optional<DepositEntity> findByTxid(String txid);

    List<DepositEntity> findByUserId(Long userId);

    List<DepositEntity> findByStatus(String status);

}
