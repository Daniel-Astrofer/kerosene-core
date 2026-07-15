package source.auth.application.orchestrator.signup.infra;

import java.util.List;

import org.springframework.stereotype.Component;

import source.auth.application.orchestrator.signup.port.PasskeyGateway;
import source.auth.application.port.out.AuthPasskeyGateway;
import source.auth.model.entity.PasskeyCredential;

@Component
public class AuthPasskeyGatewayAdapter implements PasskeyGateway {

    private final AuthPasskeyGateway delegate;

    public AuthPasskeyGatewayAdapter(AuthPasskeyGateway delegate) {
        this.delegate = delegate;
    }

    @Override
    public PasskeyCredential save(PasskeyCredential credential) {
        return delegate.save(credential);
    }

    @Override
    public List<PasskeyCredential> findByUserId(Long userId) {
        return delegate.findByUserId(userId);
    }
}
