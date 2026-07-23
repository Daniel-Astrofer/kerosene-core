package source.auth.application.orchestrator.signup.port;

import java.util.List;

import source.auth.model.entity.PasskeyCredential;

public interface PasskeyGateway {

    PasskeyCredential save(PasskeyCredential credential);

    List<PasskeyCredential> findByUserId(Long userId);
}
