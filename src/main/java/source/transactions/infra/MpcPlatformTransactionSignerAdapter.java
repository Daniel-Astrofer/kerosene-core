package source.transactions.infra;

import org.springframework.stereotype.Component;
import source.auth.AuthExceptions;
import source.auth.application.service.identityaccess.PlatformTransactionSignerPort;
import source.auth.model.entity.UserDataBase;

@Component
public class MpcPlatformTransactionSignerAdapter implements PlatformTransactionSignerPort {

    private final MpcSidecarClient mpcClient;

    public MpcPlatformTransactionSignerAdapter(MpcSidecarClient mpcClient) {
        this.mpcClient = mpcClient;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String sign(UserDataBase user) {
        throw new AuthExceptions.AuthValidationException(
                "Platform MPC signing is not available in this build. Refusing outbound co-sign requests.");
    }
}
