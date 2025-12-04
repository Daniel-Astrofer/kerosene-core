package source.wallet.orchestrator;


import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import source.auth.AuthExceptions;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.jwt.JwtService;
import source.auth.model.entity.UserDataBase;
import source.wallet.dto.WalletDTO;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletService;

import java.util.Optional;

@Component
public class WalletUseCase {

    private final UserServiceContract service;
    private final WalletService walletService;

    public WalletUseCase(UserServiceContract service,
                         WalletService walletService) {
        this.service = service;
        this.walletService = walletService;
    }

    public void  createWallet(WalletDTO dto,
                              HttpServletRequest request){

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        long id  = Long.parseLong(auth.getName());
        Optional<UserDataBase> db = service.buscarPorId(id);

        if (db.isEmpty()) {
            throw new AuthExceptions.UserNoExists("invalid user");}

        if (walletService.existsByName(dto.getName())) {
            throw new WalletExceptions.WalletNameAlredyExists("you are using this name");
        }

        WalletEntity wallet = new WalletEntity();
        wallet.setAddress(dto.getPassphrase());
        wallet.setName(dto.getName());
        wallet.setUser(db.get());
        walletService.save(wallet);
    }

    public void deleteWallet(WalletDTO dto,
                             HttpServletRequest request){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long id = Long.parseLong(auth.getName());
        if (!walletService.deleteWallet(id,dto)){
            throw new WalletExceptions.WalletNoExists("wallet no exists");
        }
    }
}
