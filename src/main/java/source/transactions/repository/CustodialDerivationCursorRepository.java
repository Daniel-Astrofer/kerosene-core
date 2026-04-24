package source.transactions.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import source.transactions.model.CustodialDerivationCursorEntity;

import java.util.Optional;

@Repository
public interface CustodialDerivationCursorRepository extends JpaRepository<CustodialDerivationCursorEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c
            from CustodialDerivationCursorEntity c
            where c.cursorKey = :cursorKey
            """)
    Optional<CustodialDerivationCursorEntity> findByCursorKeyForUpdate(@Param("cursorKey") String cursorKey);
}
