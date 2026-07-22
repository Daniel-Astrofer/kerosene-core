package source.auth.application.infra.persistence.jpa;

import java.util.List;

import org.springframework.stereotype.Component;

import source.auth.application.port.out.AuthPasskeyGateway;
import source.auth.model.entity.PasskeyCredential;

@Component
public class JpaAuthPasskeyGateway implements AuthPasskeyGateway {

    private final PasskeyCredentialRepository repository;

    public JpaAuthPasskeyGateway(PasskeyCredentialRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<PasskeyCredential> findByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    @Override
    public void deleteAll(List<PasskeyCredential> credentials) {
        repository.deleteAll(credentials);
    }

    @Override
    public PasskeyCredential save(PasskeyCredential credential) {
        return repository.save(credential);
    }
}
