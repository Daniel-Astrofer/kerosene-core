package source.auth.application.orchestrator.login;

import source.auth.AuthExceptions;
import source.auth.application.orchestrator.login.contracts.Login;
import source.auth.application.service.authentication.contracts.LoginVerifier;
import source.auth.application.service.device.UserDeviceService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.application.service.validation.totp.contratcs.TOTPVerifier;
import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.entity.UserDataBase;
import source.auth.model.entity.UserDevice;
import source.ledger.service.LedgerService;
import source.wallet.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class LoginUseCase implements Login {

    private final LoginVerifier verifier;
    private final JwtServicer service;
    private final UserDeviceService deviceService;
    private final UserServiceContract userService;
    private final TOTPVerifier totpVerifier;
    private final LedgerService ledgerService;
    private final WalletService walletService;
    
    // Saldo inicial de teste (100.000)
    private static final BigDecimal INITIAL_TEST_BALANCE = new BigDecimal("100000");

    public LoginUseCase(LoginVerifier verifier, JwtServicer service, UserDeviceService deviceService, 
                        UserServiceContract userService, TOTPVerifier totpVerifier,
                        LedgerService ledgerService, WalletService walletService) {
        this.verifier = verifier;
        this.service = service;
        this.deviceService = deviceService;
        this.userService = userService;
        this.totpVerifier = totpVerifier;
        this.ledgerService = ledgerService;
        this.walletService = walletService;
    }


    /*
     * check if the jwt is correct and assigned and give back the id from user on jwt
     * if the jwt is invalid the code check username and passphrase and throws RuntimeException if incorrect*/
    @Override
    public String loginUser(UserDTOContract dto, HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (!auth.getName().equalsIgnoreCase("anonymousUser") ){
            Long id = Long.parseLong(auth.getName());
            Optional<UserDevice>  dbDevice = deviceService.find(id);
            if (dbDevice.isPresent() && service.extractDevice(auth.getCredentials().toString()).equals(dbDevice.get().getDeviceHash())){
                return id + " " + service.generateToken(id,dbDevice.get().getDeviceHash()) ;
            }
        }

        UserDataBase user = verifier.matcher(dto, request);
        Optional<UserDevice> device = deviceService.find(user.getId());
        
        // Inicializar saldo de teste para novas contas
        initializeTestBalance(user.getId());
        
        return user.getId() + " " + service.generateToken(user.getId(),device.get().getDeviceHash()) ;


    }

    @Override
    public String loginTotpVerify(UserDTOContract dto, String deviceHash, HttpServletRequest request) {

        // validate only credentials (username + passphrase) without device enforcement
        UserDataBase user = verifier.matcherWithoutDevice(dto);
        Optional<UserDevice> deviceOpt = deviceService.find(user.getId());


        if (deviceOpt.isPresent() && deviceHash != null && deviceHash.equals(deviceOpt.get().getDeviceHash())) {
            // Inicializar saldo de teste para novas contas
            initializeTestBalance(user.getId());
            
            return user.getId() + " " + service.generateToken(user.getId(), deviceOpt.get().getDeviceHash());
        }

        if (dto.getTotpCode() == null || dto.getTotpCode().isEmpty()) {
            throw new AuthExceptions.incorrectTotp("TOTP code required when device is not recognized");
        }


        totpVerifier.totpVerify(user.getTOTPSecret(), dto.getTotpCode());

        String ip = request.getRemoteAddr();
        if (deviceHash != null && !deviceHash.isEmpty() && !deviceHash.equalsIgnoreCase("unknown")) {
            UserDevice newDevice = new UserDevice();
            newDevice.setUser(user);
            newDevice.setDeviceHash(deviceHash);
            newDevice.setIpAddress(ip);

            if (deviceOpt.isPresent()) {
                deviceService.update(deviceOpt.get().getId(), newDevice);
            } else {
                deviceService.create(newDevice);
            }

            // Inicializar saldo de teste para novas contas
            initializeTestBalance(user.getId());

            return user.getId() + " " + service.generateToken(user.getId(), deviceHash);
        }

        if (deviceOpt.isPresent()) {
            // Inicializar saldo de teste para novas contas
            initializeTestBalance(user.getId());
            
            return user.getId() + " " + service.generateToken(user.getId(), deviceOpt.get().getDeviceHash());
        }

        // Inicializar saldo de teste para novas contas
        initializeTestBalance(user.getId());

        return user.getId() + " " + service.generateToken(user.getId(), "");
    }
    
    /**
     * Inicializa saldo de teste (100.000) na primeira carteira do usuário
     * Se o usuário já tem saldo, não faz nada (apenas na primeira vez)
     */
    private void initializeTestBalance(Long userId) {
        try {
            // Obter wallets do usuário
            var wallets = walletService.findByUserId(userId);
            
            if (wallets != null && !wallets.isEmpty()) {
                // Usar a primeira wallet do usuário
                var wallet = wallets.get(0);
                
                try {
                    // Verificar se ledger já existe
                    var ledger = ledgerService.findByWalletId(wallet.getId());
                    
                    // Se o saldo for zero, adicionar o saldo de teste
                    if (ledger.getBalance().compareTo(BigDecimal.ZERO) == 0) {
                        ledgerService.updateBalance(wallet.getId(), INITIAL_TEST_BALANCE, "TEST_INITIAL_BALANCE");
                        System.out.println("✅ Saldo de teste (100.000) adicionado para usuário " + userId);
                    }
                } catch (Exception e) {
                    // Ledger não existe, criar novo com saldo de teste
                    var newLedger = ledgerService.createLedger(wallet, "TEST_INITIAL_BALANCE");
                    newLedger.setBalance(INITIAL_TEST_BALANCE);
                    System.out.println("✅ Nova carteira criada com saldo de teste (100.000) para usuário " + userId);
                }
            }
        } catch (Exception e) {
            // Não impedir o login se houver erro ao inicializar saldo
            System.err.println("⚠️  Erro ao inicializar saldo de teste: " + e.getMessage());
        }
    }
}

