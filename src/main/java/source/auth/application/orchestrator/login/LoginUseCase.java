package source.auth.application.orchestrator.login;

import source.auth.AuthExceptions;
import source.auth.application.orchestrator.login.contracts.Login;
import source.auth.application.service.authentication.contracts.LoginVerifier;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.device.UserDeviceService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.application.service.validation.totp.contratcs.TOTPVerifier;
import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.entity.UserDataBase;
import source.auth.model.entity.UserDevice;
import source.ledger.service.LedgerService;
import source.wallet.service.WalletService;
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
    private final source.notification.service.NotificationService notificationService;
    private final RedisServicer redisService;

    // Saldo inicial de teste (100.000)
    private static final BigDecimal INITIAL_TEST_BALANCE = new BigDecimal("100000");

    public LoginUseCase(LoginVerifier verifier, JwtServicer service, UserDeviceService deviceService,
            UserServiceContract userService, TOTPVerifier totpVerifier,
            LedgerService ledgerService, WalletService walletService,
            source.notification.service.NotificationService notificationService,
            RedisServicer redisService) {
        this.verifier = verifier;
        this.service = service;
        this.deviceService = deviceService;
        this.userService = userService;
        this.totpVerifier = totpVerifier;
        this.ledgerService = ledgerService;
        this.walletService = walletService;
        this.notificationService = notificationService;
        this.redisService = redisService;
    }

    @Override
    public String loginUser(UserDTOContract dto) {
        String username = dto.getUsername();
        String key = "login_failures:" + username;

        String failuresStr = redisService.getValue(key);
        int failures = failuresStr != null ? Integer.parseInt(failuresStr) : 0;

        if (failures >= 5) {
            throw new AuthExceptions.InvalidCredentials("Muitas tentativas falhas. Conta bloqueada por 15 minutos.");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (!auth.getName().equalsIgnoreCase("anonymousUser")) {
            Long id = Long.parseLong(auth.getName());
            Optional<UserDevice> dbDevice = deviceService.find(id);
            if (dbDevice.isPresent()) {
                notifyLogin(id);
                redisService.expire(key, 0); // Clear failures on success
                return id + " " + service.generateToken(id, "");
            }
        }

        try {
            UserDataBase user = verifier.matcherWithoutDevice(dto);

            // Inicializar saldo de teste para novas contas
            initializeTestBalance(user.getId());

            notifyLogin(user.getId());
            redisService.expire(key, 0); // Clear failures on success
            return user.getId() + " "
                    + service.generateToken(user.getId(), "");
        } catch (Exception e) {
            redisService.increment(key);
            redisService.expire(key, 15 * 60); // 15 minutes
            throw e;
        }
    }

    @Override
    public String loginTotpVerify(UserDTOContract dto) {
        UserDataBase user = verifier.matcherWithoutDevice(dto);

        if (dto.getTotpCode() == null || dto.getTotpCode().isEmpty()) {
            throw new AuthExceptions.incorrectTotp("TOTP code required.");
        }

        totpVerifier.totpVerify(user.getTOTPSecret(), dto.getTotpCode());

        // We removed device hashing because Tor nodes constantly change IP/Fingerprints
        Optional<UserDevice> deviceOpt = deviceService.find(user.getId());
        if (deviceOpt.isEmpty()) {
            UserDevice newDevice = new UserDevice();
            newDevice.setUser(user);
            deviceService.create(newDevice);
        }

        // Inicializar saldo de teste para novas contas
        initializeTestBalance(user.getId());

        notifyLogin(user.getId());
        return user.getId() + " " + service.generateToken(user.getId(), "");
    }

    private void notifyLogin(Long userId) {
        try {
            notificationService.notifyUser(userId, "Acesso Detectado",
                    "Um novo acesso foi identificado em sua conta Kerosene. Caso não reconheça esta ação, verifique suas sessões ativas imediatamente.");
        } catch (Exception e) {
            // Silent failure for notifications to not break login
        }
    }

    private void initializeTestBalance(Long userId) {
        try {
            var wallets = walletService.findByUserId(userId);

            if (wallets != null && !wallets.isEmpty()) {
                var wallet = wallets.get(0);

                try {
                    var ledger = ledgerService.findByWalletId(wallet.getId());

                    if (ledger.getBalance().compareTo(BigDecimal.ZERO) == 0) {
                        ledgerService.updateBalance(wallet.getId(), INITIAL_TEST_BALANCE, "TEST_INITIAL_BALANCE");

                        try {
                            String title = "Boas-vindas ao Kerosene";
                            String body = String.format(
                                    "Para iniciar suas operações, foi adicionado um saldo inicial de %s BTC em sua carteira.",
                                    INITIAL_TEST_BALANCE.toPlainString());
                            notificationService.notifyUser(userId, title, body);
                        } catch (Exception ne) {
                            // Silent failure for notifications
                        }
                    }
                } catch (Exception e) {
                    var newLedger = ledgerService.createLedger(wallet, "TEST_INITIAL_BALANCE");
                    newLedger.setBalance(INITIAL_TEST_BALANCE);
                }
            }
        } catch (Exception e) {
            // //
        }
    }
}
