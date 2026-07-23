package source.auth.application.service.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.auth.AuthExceptions;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.AccountActivationStatusDTO;
import source.auth.model.entity.UserDataBase;

import java.time.LocalDateTime;

@Service
public class AccountActivationService {

    private final UserServiceContract userService;

    public AccountActivationService(UserServiceContract userService) {
        this.userService = userService;
    }

    public AccountActivationStatusDTO getStatus(Long userId) {
        UserDataBase user = requireUser(userId);
        return AccountActivationStatusDTO.from(new ActivationUserView(user));
    }

    @Transactional
    public AccountActivationStatusDTO createOrReuseLink(Long userId) {
        UserDataBase user = requireUser(userId);
        return AccountActivationStatusDTO.from(new ActivationUserView(user));
    }

    @Transactional
    public AccountActivationStatusDTO confirm(Long userId, String linkId, String txid, String fromAddress) {
        throw new AuthExceptions.AuthValidationException(
                "O deposito inicial agora deve ser feito dentro da plataforma, nao por link de ativacao.");
    }

    @Transactional
    public UserDataBase activateUser(Long userId) {
        UserDataBase user = requireUser(userId);
        if (Boolean.TRUE.equals(user.getIsActive())) {
            return user;
        }
        user.setIsActive(true);
        user.setActivatedAt(LocalDateTime.now());
        return userService.createUserInDataBase(user);
    }

    public void assertInboundEnabled(Long userId) {
        assertInboundEnabled(requireUser(userId));
    }

    public void assertInboundEnabled(UserDataBase user) {
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new AuthExceptions.InboundReceivingBlockedException(
                    AccountActivationStatusDTO.INBOUND_BLOCKED_MESSAGE);
        }
    }

    private UserDataBase requireUser(Long userId) {
        return userService.buscarPorId(userId)
                .orElseThrow(() -> new AuthExceptions.InvalidCredentials("Authenticated user not found."));
    }

    private record ActivationUserView(UserDataBase user) implements AccountActivationStatusDTO.UserDataBaseView {
        @Override
        public boolean isActive() {
            return Boolean.TRUE.equals(user.getIsActive());
        }

        @Override
        public LocalDateTime activatedAt() {
            return user.getActivatedAt();
        }
    }
}
