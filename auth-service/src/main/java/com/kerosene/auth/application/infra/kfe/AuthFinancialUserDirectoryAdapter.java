package com.kerosene.auth.application.infra.kfe;

import org.springframework.stereotype.Service;
import com.kerosene.auth.application.infra.persistence.jpa.UserRepository;
import com.kerosene.auth.model.entity.UserDataBase;
import com.kerosene.common.financial.operations.FinancialUserDirectoryPort;

import java.util.Locale;
import java.util.Optional;

/** Resolves financial directory handles from the authentication service's user repository. */
@Service
public class AuthFinancialUserDirectoryAdapter implements FinancialUserDirectoryPort {

    /** Persistence adapter for user identity lookup. */
    private final UserRepository userRepository;

    /**
     * Creates the directory adapter with the canonical auth user repository.
     *
     * @param userRepository persistence adapter for user records
     */
    public AuthFinancialUserDirectoryAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Resolves a case-insensitive username after trimming whitespace and leading @ markers.
     * Empty input or a prefix-only value yields no match without querying the repository.
     *
     * @param username username or handle supplied by the caller
     * @return user handle when found, otherwise empty
     */
    @Override
    public Optional<FinancialUserHandle> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String normalized = username.trim();
        while (normalized.startsWith("@")) {
            normalized = normalized.substring(1).trim();
        }
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return toHandle(userRepository.findByUsername(normalized.toLowerCase(Locale.ROOT)));
    }

    /**
     * Resolves a user by its persisted numeric ID.
     *
     * @param userId database user ID, nullable
     * @return handle when the row exists, otherwise empty
     */
    @Override
    public Optional<FinancialUserHandle> findById(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId).flatMap(this::toHandle);
    }

    /**
     * Projects a persisted auth entity into the small financial user-directory contract.
     *
     * @param user user entity returned by persistence
     * @return empty for null, otherwise ID/username/active-state handle
     */
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
