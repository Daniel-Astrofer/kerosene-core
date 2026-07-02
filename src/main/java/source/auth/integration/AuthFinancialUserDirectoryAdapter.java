package source.auth.integration;

import org.springframework.stereotype.Service;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.model.entity.UserDataBase;
import source.common.financial.FinancialUserDirectoryPort;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthFinancialUserDirectoryAdapter implements FinancialUserDirectoryPort {

    private final UserRepository userRepository;

    public AuthFinancialUserDirectoryAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<FinancialUserHandle> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return toHandle(userRepository.findByUsername(username.trim().toLowerCase(Locale.ROOT)));
    }

    @Override
    public Optional<FinancialUserHandle> findById(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId).flatMap(this::toHandle);
    }

    private Optional<FinancialUserHandle> toHandle(UserDataBase user) {
        if (user == null) {
            return Optional.empty();
        }
        return Optional.of(new FinancialUserHandle(
                user.getId(),
                user.getUsername(),
                Boolean.TRUE.equals(user.getIsActive())));
    }
}
