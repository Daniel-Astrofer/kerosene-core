package com.kerosene.auth.application.infra.persistence.jpa;

import java.util.List;

import org.springframework.stereotype.Component;

import com.kerosene.auth.application.port.out.AuthPasskeyGateway;
import com.kerosene.auth.model.entity.PasskeyCredential;

/** Adapts the authentication passkey port to its Spring Data JPA repository. */
@Component
public class JpaAuthPasskeyGateway implements AuthPasskeyGateway {

    /** Persistence adapter for passkey credential rows. */
    private final PasskeyCredentialRepository repository;

    /**
     * Creates the gateway with the passkey persistence repository.
     *
     * @param repository passkey credential repository
     */
    public JpaAuthPasskeyGateway(PasskeyCredentialRepository repository) {
        this.repository = repository;
    }

    /** Lists passkey credentials belonging to a user. */
    @Override
    public List<PasskeyCredential> findByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    /** Deletes the supplied credential entities through the repository unit of work. */
    @Override
    public void deleteAll(List<PasskeyCredential> credentials) {
        repository.deleteAll(credentials);
    }

    /** Saves a passkey credential and returns its persistence-managed representation. */
    @Override
    public PasskeyCredential save(PasskeyCredential credential) {
        return repository.save(credential);
    }
}
