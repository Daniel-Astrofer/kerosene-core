package source.ledger.infra.transaction;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import source.auth.AuthExceptions;
import source.ledger.application.transaction.AuthenticatedUserPort;

@Component
public class SecurityContextAuthenticatedUserAdapter implements AuthenticatedUserPort {

    @Override
    public Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthExceptions.InvalidCredentials("Not authenticated.");
        }

        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new AuthExceptions.InvalidCredentials("Invalid authentication context.");
        }
    }
}
