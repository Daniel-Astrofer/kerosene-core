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
        ResourceBundle bundle = null;
        try {
            bundle = ResourceBundle.getBundle(BUNDLE_NAME, resolvedLocale);
        } catch (MissingResourceException missing) {
            // Bundle is optional in some images / partial classpaths; keys still format.
            bundle = null;
        }
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
        String pattern = key;
        if (bundle != null && key != null && bundle.containsKey(key)) {
            try {
                pattern = bundle.getString(key);
            } catch (MissingResourceException | ClassCastException ignored) {
                pattern = key;
            }
        }
        if (args == null || args.length == 0 || pattern == null) {
            return pattern;
        }
        try {
            return new MessageFormat(pattern, locale != null ? locale : DEFAULT_LOCALE).format(args);
        } catch (IllegalArgumentException ignored) {
            return pattern;
        }
    }
}
