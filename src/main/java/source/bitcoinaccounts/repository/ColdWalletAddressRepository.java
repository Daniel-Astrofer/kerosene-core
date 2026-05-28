package source.bitcoinaccounts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.bitcoinaccounts.model.ColdWalletAddressEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ColdWalletAddressRepository extends JpaRepository<ColdWalletAddressEntity, UUID> {

    List<ColdWalletAddressEntity> findByColdWalletId(UUID coldWalletId);
}
