package source.auth.application.service.identityaccess;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import source.auth.model.entity.UserDataBase;

@Component
@Profile("!prod")
public class UnavailablePlatformTransactionSigner implements PlatformTransactionSignerPort {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String sign(UserDataBase user) {
        throw new IllegalStateException("Platform transaction co-signing is not configured.");
    }
}
