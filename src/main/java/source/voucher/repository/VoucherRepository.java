package source.voucher.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import source.auth.model.entity.Voucher;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, UUID> {

    Optional<Voucher> findByCode(String code);

    Optional<Voucher> findByTxid(String txid);

    @Modifying
    @Query("DELETE FROM Voucher v WHERE v.status = 'PENDING' AND v.createdAt < :date")
    int deletePendingOlderThan(@Param("date") LocalDateTime date);
}
