package source.bitcoinaccounts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.model.ReceivingAddressEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceivingAddressRepository extends JpaRepository<ReceivingAddressEntity, UUID> {

    Optional<ReceivingAddressEntity> findByAddress(String address);

    List<ReceivingAddressEntity> findTop200ByStatusInOrderByUpdatedAtAsc(
            List<BitcoinAccountEnums.ReceivingAddressStatus> statuses);
}
