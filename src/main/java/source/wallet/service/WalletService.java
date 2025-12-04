package source.wallet.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import source.auth.application.orchestrator.login.contracts.Signup;
import source.auth.application.service.authentication.contracts.SignupVerifier;
import source.auth.application.service.cripto.contracts.Hasher;
import source.wallet.dto.WalletDTO;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;
import java.util.List;


@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final Hasher hash;
    private final SignupVerifier verify;

    public WalletService(WalletRepository walletRepository,
                         @Qualifier("SHAHasher") Hasher hash, SignupVerifier verify) {
        this.walletRepository = walletRepository;
        this.hash = hash;
        this.verify = verify;
    }

    public void save(WalletEntity entity) {
        verify.checkPassphraseBip39(entity.getAddress());
        entity.setAddress(hash.hash(entity.getAddress()));
        walletRepository.save(entity);
    }

    public WalletEntity findByName(String name){ return walletRepository.findByName(name);}
    public boolean existsByName(String name){return walletRepository.existsByName(name);}

    public List<WalletEntity> findByUserId(Long userId){
        return walletRepository.findByUserId(userId);
    }

    public boolean deleteWallet(Long id,WalletDTO wallet){
        wallet.setPassphrase(hash.hash(wallet.getPassphrase()));
        List<WalletEntity> dbWallet = walletRepository.findByUserId(id);
        if (dbWallet.isEmpty()){
            throw new WalletExceptions.WalletNoExists("you no have any wallet");
        }
        for (WalletEntity walletName: dbWallet){
            if (walletName.getName().equals(wallet.getName())){
                walletRepository.delete(walletName);
                return true;
            }
        }
        return false;
    }


}
