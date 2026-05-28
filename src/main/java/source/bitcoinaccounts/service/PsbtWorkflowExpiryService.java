package source.bitcoinaccounts.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PsbtWorkflowExpiryService {

    private final PsbtWorkflowService psbtWorkflowService;

    public PsbtWorkflowExpiryService(PsbtWorkflowService psbtWorkflowService) {
        this.psbtWorkflowService = psbtWorkflowService;
    }

    @Scheduled(fixedDelayString = "${bitcoin-accounts.psbt.expiry-fixed-delay-ms:300000}")
    public void expirePendingWorkflows() {
        psbtWorkflowService.expirePendingWorkflows();
    }
}
