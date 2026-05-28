package source.auth.application.service.account;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.BackupCodesStatusDTO;
import source.auth.model.entity.UserDataBase;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class BackupCodeService {

    private static final int BACKUP_CODE_COUNT = 10;
    private static final int BACKUP_CODE_BOUND = 100_000_000;

    private final UserServiceContract userService;
    private final Hasher hasher;
    private final SecureRandom random = new SecureRandom();

    public BackupCodeService(
            UserServiceContract userService,
            @Qualifier("Argon2Hasher") Hasher hasher) {
        this.userService = userService;
        this.hasher = hasher;
    }

    public BackupCodesStatusDTO getStatus(Long userId) {
        UserDataBase user = requireUser(userId);
        int remaining = user.getBackupCodes() != null ? user.getBackupCodes().size() : 0;
        return new BackupCodesStatusDTO(remaining > 0, remaining, List.of());
    }

    @Transactional
    public BackupCodesStatusDTO regenerate(Long userId) {
        UserDataBase user = requireUser(userId);
        GeneratedBackupCodes generated = generateBackupCodes();
        user.setBackupCodes(generated.hashedCodes());
        userService.createUserInDataBase(user);
        return new BackupCodesStatusDTO(true, generated.hashedCodes().size(), generated.rawCodes());
    }

    private UserDataBase requireUser(Long userId) {
        return userService.buscarPorId(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found."));
    }

    private GeneratedBackupCodes generateBackupCodes() {
        List<String> rawCodes = new ArrayList<>();
        List<String> hashedCodes = new ArrayList<>();

        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String code = String.format("%08d", random.nextInt(BACKUP_CODE_BOUND));
            rawCodes.add(code);
            char[] codeChars = code.toCharArray();
            try {
                hashedCodes.add(hasher.hash(codeChars));
            } finally {
                Arrays.fill(codeChars, '\0');
            }
        }

        return new GeneratedBackupCodes(rawCodes, hashedCodes);
    }

    private record GeneratedBackupCodes(List<String> rawCodes, List<String> hashedCodes) {
    }
}
