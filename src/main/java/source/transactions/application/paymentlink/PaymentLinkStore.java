package source.transactions.application.paymentlink;

import source.transactions.dto.PaymentLinkDTO;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface PaymentLinkStore {

    PaymentLinkDTO save(PaymentLinkDTO paymentLink);

    PaymentLinkDTO save(PaymentLinkDTO paymentLink, Duration ttl);

    Optional<PaymentLinkDTO> findById(String linkId);

    List<PaymentLinkDTO> findByUserId(Long userId);

    List<PaymentLinkDTO> findByStatus(String status);

    void delete(String linkId);
}
