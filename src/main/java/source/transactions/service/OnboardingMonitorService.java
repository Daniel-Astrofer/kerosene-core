package source.transactions.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import source.transactions.application.paymentlink.PaymentLinkStatus;
import source.transactions.application.paymentlink.PaymentLinkStore;
import source.transactions.dto.PaymentLinkDTO;
import source.transactions.infra.BlockchainClient;

import java.util.List;

@Service
public class OnboardingMonitorService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingMonitorService.class);

    private final PaymentLinkStore paymentLinkStore;
    private final OnboardingPaymentFinalizer onboardingPaymentFinalizer;
    private final BlockchainClient blockchainClient;
    private final boolean voucherMockAcceptAnyTxid;
    private final int requiredConfirmations;

    public OnboardingMonitorService(PaymentLinkStore paymentLinkStore,
            OnboardingPaymentFinalizer onboardingPaymentFinalizer,
            BlockchainClient blockchainClient,
            @Value("${voucher.mock.accept-any-txid:false}") boolean voucherMockAcceptAnyTxid,
            @Value("${bitcoin.min-confirmations:3}") int requiredConfirmations) {
        this.paymentLinkStore = paymentLinkStore;
        this.onboardingPaymentFinalizer = onboardingPaymentFinalizer;
        this.blockchainClient = blockchainClient;
        this.voucherMockAcceptAnyTxid = voucherMockAcceptAnyTxid;
        this.requiredConfirmations = Math.max(1, requiredConfirmations);
    }

    /**
     * Checks pending onboarding transactions every 1 minute.
     * Looks for payment links that are flagged as "verifying_onboarding"
     */
    @Scheduled(fixedDelay = 60000)
    public void monitorOnboardingConfirmations() {
        List<PaymentLinkDTO> pendingLinks = paymentLinkStore.findByStatus(PaymentLinkStatus.VERIFYING_ONBOARDING);
        if (pendingLinks.isEmpty()) {
            return;
        }

        log.info("Checking onboarding transaction confirmations...");
        for (PaymentLinkDTO dto : pendingLinks) {
            checkConfirmations(dto);
        }
    }

    private void checkConfirmations(PaymentLinkDTO dto) {
        try {
            if (dto.getTxid() == null)
                return;

            if (voucherMockAcceptAnyTxid) {
                log.warn("Mock voucher mode enabled. Finalizing onboarding link {} without blockchain lookup.",
                        dto.getId());
                onboardingPaymentFinalizer.finalizeConfirmedPayment(dto);
                return;
            }

            JsonNode txInfo = blockchainClient.getRawTransaction(dto.getTxid(), true);

            if (txInfo == null || txInfo.isNull() || txInfo.isMissingNode()) {
                log.warn("Onboarding tx {} not found on blockchain", dto.getTxid());
                return;
            }

            int confirmations = txInfo.path("confirmations").isNumber()
                    ? txInfo.path("confirmations").asInt()
                    : 0;
            log.info("Onboarding link {} has {} confirmations (Target: {})", dto.getId(), confirmations,
                    requiredConfirmations);

            if (confirmations >= requiredConfirmations) {
                // We reached 3 confirmations!
                // Finish registration in Postgres
                log.info("3 confirmations reached for link {}! Finalizing user account for session {}.", dto.getId(),
                        dto.getSessionId());

                onboardingPaymentFinalizer.finalizeConfirmedPayment(dto);
            }
        } catch (Exception e) {
            log.error("Failed to check confirmations for onboarding link {}", dto.getId(), e);
        }
    }
}
