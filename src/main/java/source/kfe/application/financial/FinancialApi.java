package source.kfe.application.financial;

import org.springframework.stereotype.Service;
import source.kfe.dto.KfeAddressResponse;
import source.kfe.dto.KfeColdWalletPsbtRequest;
import source.kfe.dto.KfeColdWalletPsbtResponse;
import source.kfe.dto.KfeCreateWalletRequest;
import source.kfe.dto.KfeReceivingCapabilitiesResponse;
import source.kfe.dto.KfeSubmitTransactionRequest;
import source.kfe.dto.KfeTransactionResponse;
import source.kfe.dto.KfeUtxoResponse;
import source.kfe.dto.KfeWalletNameOption;
import source.kfe.dto.KfeWalletResponse;
import source.kfe.repository.KfeTransactionRepository;
import source.kfe.service.KfeResponseMapper;
import source.kfe.service.KfeTransactionEngine;
import source.kfe.service.KfeWalletNetworkService;
import source.kfe.service.KfeWalletService;

import java.util.List;
import java.util.UUID;

@Service
public class FinancialApi {

    private final KfeTransactionEngine transactionEngine;
    private final KfeTransactionRepository transactionRepository;
    private final KfeResponseMapper responseMapper;
    private final KfeWalletService walletService;
    private final KfeWalletNetworkService walletNetworkService;

    public FinancialApi(
            KfeTransactionEngine transactionEngine,
            KfeTransactionRepository transactionRepository,
            KfeResponseMapper responseMapper,
            KfeWalletService walletService,
            KfeWalletNetworkService walletNetworkService) {
        this.transactionEngine = transactionEngine;
        this.transactionRepository = transactionRepository;
        this.responseMapper = responseMapper;
        this.walletService = walletService;
        this.walletNetworkService = walletNetworkService;
    }

    public KfeTransactionResponse submitTransaction(Long userId, KfeSubmitTransactionRequest request) {
        return transactionEngine.submit(userId, request);
    }

    public KfeTransactionResponse existingTransactionByIdempotency(
            Long userId,
            String idempotencyKey,
            String requestHash) {
        return transactionEngine.getExistingByIdempotency(userId, idempotencyKey, requestHash);
    }

    public String transactionRequestHash(Long userId, KfeSubmitTransactionRequest request) {
        return transactionEngine.requestHash(userId, request);
    }

    public KfeTransactionResponse transaction(Long userId, UUID transactionId) {
        return transactionRepository
                .findByIdAndUserId(transactionId, userId)
                .map(responseMapper::toTransactionResponse)
                .orElseThrow(() -> new IllegalArgumentException("KFE transaction not found."));
    }

    public KfeWalletResponse createWallet(Long userId, KfeCreateWalletRequest request) {
        return walletService.createWallet(userId, request);
    }

    public List<KfeWalletResponse> wallets(Long userId) {
        return walletService.listWallets(userId);
    }

    public List<KfeWalletNameOption> walletNames() {
        return walletService.availableWalletNames();
    }

    public KfeAddressResponse rotateAddress(Long userId, UUID walletId) {
        return walletService.rotateAddress(userId, walletId);
    }

    public List<KfeUtxoResponse> walletUtxos(Long userId, UUID walletId) {
        return walletNetworkService.listUtxos(userId, walletId);
    }

    public KfeColdWalletPsbtResponse createColdWalletPsbt(
            Long userId,
            UUID walletId,
            KfeColdWalletPsbtRequest request) {
        return walletNetworkService.createColdWalletPsbt(userId, walletId, request);
    }

    public KfeReceivingCapabilitiesResponse receivingCapabilities(String receiverIdentifier) {
        return walletNetworkService.receivingCapabilities(receiverIdentifier);
    }
}
