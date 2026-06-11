package source.transactions.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import source.common.infra.RedisAvailabilityGuard;
import source.transactions.application.paymentlink.PaymentLinkStatus;
import source.transactions.application.paymentlink.PaymentLinkStore;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.infra.BlockchainClient;

import java.util.List;

@Service
@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")
public class AccountActivationMonitorService {

    private static final Logger log = LoggerFactory.getLogger(AccountActivationMonitorService.class);

    private final PaymentLinkStore paymentLinkStore;
    private final AccountActivationPaymentFinalizer paymentFinalizer;
    private final BlockchainClient blockchainClient;
    private final RedisAvailabilityGuard redisAvailabilityGuard;
    private final boolean mockAcceptAnyTxid;
    private final int requiredConfirmations;

    public AccountActivationMonitorService(
            PaymentLinkStore paymentLinkStore,
            AccountActivationPaymentFinalizer paymentFinalizer,
            BlockchainClient blockchainClient,
            RedisAvailabilityGuard redisAvailabilityGuard,
            @Value("${voucher.mock.accept-any-txid:false}") boolean mockAcceptAnyTxid,
            @Value("${bitcoin.min-confirmations:3}") int requiredConfirmations) {
        this.paymentLinkStore = paymentLinkStore;
        this.paymentFinalizer = paymentFinalizer;
        this.blockchainClient = blockchainClient;
        this.redisAvailabilityGuard = redisAvailabilityGuard;
        this.mockAcceptAnyTxid = mockAcceptAnyTxid;
        this.requiredConfirmations = Math.max(1, requiredConfirmations);
    }

    @Scheduled(fixedDelay = 60000)
    public void monitorActivationConfirmations() {
        if (!redisAvailabilityGuard.isAvailable()) {
            log.debug("Skipping activation monitor cycle because Redis is unavailable: {}",
                    redisAvailabilityGuard.describeLastFailure());
            return;
        }

        List<PaymentLinkDTO> pendingLinks = paymentLinkStore.findByStatus(PaymentLinkStatus.VERIFYING_ACTIVATION);
        for (PaymentLinkDTO link : pendingLinks) {
            checkConfirmations(link);
        }
    }

    private void checkConfirmations(PaymentLinkDTO link) {
        try {
            if (link.getTxid() == null || link.getTxid().isBlank()) {
                return;
            }
            if (mockAcceptAnyTxid) {
                paymentFinalizer.finalizeConfirmedPayment(link);
                return;
            }

            JsonNode txInfo = blockchainClient.getRawTransaction(link.getTxid(), true);
            if (txInfo == null || txInfo.isNull() || txInfo.isMissingNode()) {
                return;
            }

            int confirmations = txInfo.path("confirmations").isNumber()
                    ? txInfo.path("confirmations").asInt()
                    : 0;

            log.info("Account activation link {} has {} confirmations (target={})",
                    link.getId(), confirmations, requiredConfirmations);

            if (confirmations >= requiredConfirmations) {
                paymentFinalizer.finalizeConfirmedPayment(link);
            }
        } catch (Exception e) {
            log.error("Failed to check confirmations for activation link {}", link.getId(), e);
        }
    }
}
