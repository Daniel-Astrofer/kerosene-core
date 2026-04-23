package source.wallet.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import source.wallet.model.WalletEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<WalletEntity, Long> {

    WalletEntity findByName(String name);

    WalletEntity findByPassphraseHash(String passphraseHash);

    List<WalletEntity> findByUserId(Long id);

    boolean existsByName(String name);

    boolean existsByUserIdAndName(Long id, String name);

    Optional<WalletEntity> findByUserIdAndName(Long userId, String name);

    Optional<WalletEntity> findByDepositAddress(String depositAddress);

    Optional<WalletEntity> findByLightningAddress(String lightningAddress);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletEntity w WHERE w.id = :id")
    Optional<WalletEntity> findByIdForUpdate(@Param("id") Long id);
}
