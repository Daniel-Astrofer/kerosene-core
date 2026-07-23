package com.kerosene.auth.application.orchestrator.login;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.kerosene.auth.application.service.validation.jwt.contracts.JwtServicer;
import com.kerosene.auth.model.entity.UserDataBase;
import com.kerosene.notification.l10n.NotificationMessageKey;
import com.kerosene.notification.l10n.NotificationMessages;
import com.kerosene.notification.model.NotificationKind;
import com.kerosene.notification.model.NotificationSeverity;
import com.kerosene.notification.service.NotificationService;

import java.util.List;
import java.util.Map;

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
        return user.getId() + " " + jwtService.generateToken(user.getId(), List.of(user.getRole().name()));
    }

    private void notifyLogin(Long userId) {
        try {
            notificationService.notifyUser(
                    userId,
                    NotificationMessages.payload(
                            NotificationKind.SECURITY_LOGIN_DETECTED,
                            NotificationSeverity.WARNING,
                            NotificationMessageKey.SECURITY_LOGIN_DETECTED,
                            "/settings",
                            "user",
                            String.valueOf(userId),
                            Map.of("scope", "session")));
        } catch (Exception e) {
            log.warn("Falha ao enviar notificação de login para usuário {}", userId, e);
        }
    }
}
