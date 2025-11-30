package source.wallet.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.wallet.model.WalletEntity;

@Repository
public interface WalletRepository extends JpaRepository<WalletEntity, Long> {
}
