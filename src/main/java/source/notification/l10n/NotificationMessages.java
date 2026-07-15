package source.notification.l10n;

import source.notification.model.NotificationKind;
import source.notification.model.NotificationSeverity;
import source.notification.model.UserNotificationPayload;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class NotificationMessages {

    private static final String BUNDLE_NAME = "notifications";
    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("pt-BR");

    private NotificationMessages() {
    }

    public static LocalizedNotificationMessage resolve(NotificationMessageKey key, Object... args) {
        return resolve(DEFAULT_LOCALE, key, args);
    }

    public static LocalizedNotificationMessage resolve(Locale locale, NotificationMessageKey key, Object... args) {
        Locale resolvedLocale = locale != null ? locale : DEFAULT_LOCALE;
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, resolvedLocale);
        return new LocalizedNotificationMessage(
                format(bundle, resolvedLocale, key.titleKey(), args),
                format(bundle, resolvedLocale, key.bodyKey(), args));
    }

    public static UserNotificationPayload payload(
            NotificationKind kind,
            NotificationSeverity severity,
            NotificationMessageKey key,
            String deeplink,
            String entityType,
            String entityId,
            Map<String, String> metadata,
            Object... args) {
        LocalizedNotificationMessage message = resolve(key, args);
        return UserNotificationPayload.create(
                kind,
                severity,
                message.title(),
                message.body(),
                deeplink,
                entityType,
                entityId,
                metadata);
    }

    private static String format(ResourceBundle bundle, Locale locale, String key, Object... args) {
        try {
            return new MessageFormat(bundle.getString(key), locale).format(args);
        } catch (MissingResourceException exception) {
            throw new IllegalArgumentException("Missing notification message key: " + key, exception);
        }
    }
}
