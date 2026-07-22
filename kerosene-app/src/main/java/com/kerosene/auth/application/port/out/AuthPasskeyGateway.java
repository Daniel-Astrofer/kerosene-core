package source.auth.application.port.out;

import java.util.List;

import source.auth.model.entity.PasskeyCredential;

public interface AuthPasskeyGateway {

    List<PasskeyCredential> findByUserId(Long userId);

    void deleteAll(List<PasskeyCredential> credentials);

    PasskeyCredential save(PasskeyCredential credential);
}
