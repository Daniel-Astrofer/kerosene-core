package source.auth.application.orchestrator.login;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.model.entity.UserDataBase;
import source.notification.service.NotificationService;

@Component
public class IssueSessionToken {

    private static final Logger log = LoggerFactory.getLogger(IssueSessionToken.class);

    private final JwtServicer jwtService;
    private final NotificationService notificationService;

    public IssueSessionToken(JwtServicer jwtService,
            @Lazy
            NotificationService notificationService) {
        this.jwtService = jwtService;
        this.notificationService = notificationService;
    }

    public String issue(UserDataBase user) {
        notifyLogin(user.getId());
        return user.getId() + " " + jwtService.generateToken(user.getId());
    }

    private void notifyLogin(Long userId) {
        try {
            notificationService.notifyUser(userId, "Acesso Detectado",
                    "Um novo acesso foi identificado em sua conta Kerosene. Caso não reconheça esta ação, verifique suas sessões ativas imediatamente.");
        } catch (Exception e) {
            log.warn("Falha ao enviar notificação de login para usuário {}", userId, e);
        }
    }
}
