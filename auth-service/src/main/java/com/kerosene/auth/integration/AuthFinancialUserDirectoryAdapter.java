package com.kerosene.auth.integration;

import org.springframework.stereotype.Service;
import com.kerosene.auth.application.infra.persistence.jpa.UserRepository;
import com.kerosene.auth.model.entity.UserDataBase;
import com.kerosene.common.financial.FinancialUserDirectoryPort;

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
        String normalized = username.trim();
        while (normalized.startsWith("@")) {
            normalized = normalized.substring(1).trim();
        }
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return toHandle(userRepository.findByUsername(normalized.toLowerCase(Locale.ROOT)));
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
