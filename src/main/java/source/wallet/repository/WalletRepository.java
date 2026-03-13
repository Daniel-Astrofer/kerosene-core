package source.wallet.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
