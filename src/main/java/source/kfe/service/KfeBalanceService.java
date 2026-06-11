package source.kfe.service;

import org.springframework.stereotype.Service;
import source.kfe.model.KfeBalanceEntity;
import source.kfe.model.KfeBalanceId;
import source.kfe.repository.KfeBalanceRepository;

import java.util.UUID;

@Service
public class KfeBalanceService {

    private final KfeBalanceRepository balanceRepository;
    private final KfeHashService hashService;

    public KfeBalanceService(KfeBalanceRepository balanceRepository, KfeHashService hashService) {
        this.balanceRepository = balanceRepository;
        this.hashService = hashService;
    }

    public KfeBalanceEntity createEmptyBalance(UUID walletId, String asset) {
        String normalizedAsset = asset != null ? asset : "BTC";
        String initialHash = hashService.initialBalanceHash(walletId.toString(), normalizedAsset);
        KfeBalanceEntity balance = KfeBalanceEntity.empty(walletId, normalizedAsset, initialHash);
        balance.setBalanceSignature(hashService.balanceHash(balance));
        return balanceRepository.save(balance);
    }

    public KfeBalanceEntity requireForUpdate(UUID walletId, String asset) {
        return balanceRepository.findByWalletIdAndAssetForUpdate(walletId, asset != null ? asset : "BTC")
                .orElseThrow(() -> new IllegalArgumentException("KFE balance not found for wallet " + walletId + "."));
    }

    public KfeBalanceEntity reserve(UUID walletId, String asset, long amountSats) {
        KfeBalanceEntity balance = requireForUpdate(walletId, asset);
        balance.reserve(amountSats);
        sign(balance);
        return balanceRepository.save(balance);
    }

    public KfeBalanceEntity settleReservedDebit(UUID walletId, String asset, long amountSats) {
        KfeBalanceEntity balance = requireForUpdate(walletId, asset);
        balance.settleReservedDebit(amountSats);
        sign(balance);
        return balanceRepository.save(balance);
    }

    public KfeBalanceEntity releaseReserved(UUID walletId, String asset, long amountSats) {
        KfeBalanceEntity balance = requireForUpdate(walletId, asset);
        balance.releaseReserved(amountSats);
        sign(balance);
        return balanceRepository.save(balance);
    }

    public KfeBalanceEntity creditAvailable(UUID walletId, String asset, long amountSats) {
        KfeBalanceEntity balance = requireForUpdate(walletId, asset);
        balance.creditAvailable(amountSats);
        sign(balance);
        return balanceRepository.save(balance);
    }

    public KfeBalanceEntity setObserved(UUID walletId, String asset, long observedSats) {
        KfeBalanceEntity balance = requireForUpdate(walletId, asset);
        balance.setObservedBalance(observedSats);
        sign(balance);
        return balanceRepository.save(balance);
    }

    private void sign(KfeBalanceEntity balance) {
        String hash = hashService.balanceHash(balance);
        balance.setLastHash(hash);
        balance.setBalanceSignature(hash);
    }
}
