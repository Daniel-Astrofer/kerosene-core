package com.kerosene.content.service;

import org.springframework.stereotype.Service;
import com.kerosene.auth.application.infra.persistence.jpa.UserRepository;
import com.kerosene.auth.application.port.out.AuthPasskeyGateway;
import com.kerosene.auth.model.entity.UserDataBase;
import com.kerosene.content.dto.HomeFeedCtaDTO;
import com.kerosene.content.dto.HomeFeedItemDTO;
import com.kerosene.content.dto.HomeFeedMediaDTO;
import com.kerosene.content.dto.HomeFeedResponseDTO;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Composes a personalized home content feed without a CMS admin UI.
 *
 * <p>Catalog lives in code (seed). Rules use authenticated user signals:
 * locale, balance surface, passkey presence, TOTP presence, stable user bucket.
 */
@Service
public class HomeFeedComposer {

    /** Maximum total number of cards sent in one feed response. */
    private static final int MAX_ITEMS = 8;
    /** Cap paid/security promos only — FEATURE and ANNOUNCEMENT cards can still fill the feed. */
    private static final int MAX_PROMOS = 2;
    /** Client cache lifetime for a composed feed snapshot. */
    private static final int TTL_SECONDS = 300;

    /** Source of account identity and security signals used to personalize the catalog. */
    private final UserRepository userRepository;
    /** Passkey lookup port used to decide whether a security promotion is relevant. */
    private final AuthPasskeyGateway passkeyGateway;
    /** Product-owned tier rules used to create the secured-card education cards. */
    private final WalletCardTierCatalog walletCardTierCatalog;

    /**
     * Injects account signals, passkey lookup, and wallet-card tier policy dependencies.
     * @param userRepository account and TOTP state source
     * @param passkeyGateway passkey presence lookup
     * @param walletCardTierCatalog card-tier fee and eligibility catalog
     */
    public HomeFeedComposer(
            UserRepository userRepository,
            AuthPasskeyGateway passkeyGateway,
            WalletCardTierCatalog walletCardTierCatalog) {
        this.userRepository = userRepository;
        this.passkeyGateway = passkeyGateway;
        this.walletCardTierCatalog = walletCardTierCatalog;
    }

    /**
     * Composes a personalized feed using the default UTC timezone.
     * @param userId authenticated account identifier, or null for anonymous feed composition
     * @param balanceViewRaw requested balance presentation selector
     * @param localeRaw requested language/locale tag
     * @return cached-snapshot envelope containing the selected feed cards
     */
    public HomeFeedResponseDTO compose(
            Long userId,
            String balanceViewRaw,
            String localeRaw) {
        return compose(userId, balanceViewRaw, localeRaw, null);
    }

    /**
     * Normalizes request context, filters and ranks the catalog, applies card caps, and supplies fallback education.
     * @param userId authenticated account identifier, or null for anonymous feed composition
     * @param balanceViewRaw requested balance presentation selector
     * @param localeRaw requested language/locale tag
     * @param timeZoneRaw requested timezone identifier
     * @return response with version timestamp, TTL, normalized context, and selected cards
     */
    public HomeFeedResponseDTO compose(
            Long userId,
            String balanceViewRaw,
            String localeRaw,
            String timeZoneRaw) {
        String balanceView = normalizeBalanceView(balanceViewRaw);
        String locale = HomeLocaleSupport.normalizeLocale(localeRaw);
        String timeZone = normalizeTimeZone(timeZoneRaw);
        UserSignals signals = loadSignals(userId);

        List<CatalogEntry> matched = new ArrayList<>();
        for (CatalogEntry entry : catalog()) {
            if (entry.matches(balanceView, locale, signals)) {
                matched.add(entry);
            }
        }
        matched.sort(Comparator
                .comparingInt(CatalogEntry::priority)
                .reversed()
                .thenComparing(CatalogEntry::id));

        List<HomeFeedItemDTO> items = new ArrayList<>();
        int promos = 0;
        for (CatalogEntry entry : matched) {
            // Only pure PROMO slots are rate-limited. Card ANNOUNCEMENT / FEATURE
            // product shots must not be starved by passkey/TOTP promos.
            if ("PROMO".equals(entry.kind())) {
                if (promos >= MAX_PROMOS) {
                    continue;
                }
                promos++;
            }
            items.add(entry.toDto(locale));
            if (items.size() >= MAX_ITEMS) {
                break;
            }
        }

        if (items.isEmpty()) {
            items = fallback(balanceView, locale);
        }

        return new HomeFeedResponseDTO(
                Instant.now().toString(),
                TTL_SECONDS,
                balanceView,
                locale,
                timeZone,
                List.copyOf(items));
    }

    /**
     * Accepts a bounded timezone value and falls back to UTC for missing or oversized input.
     * @param raw client-supplied timezone
     * @return trimmed timezone text or UTC
     */
    private String normalizeTimeZone(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UTC";
        }
        String value = raw.trim();
        // Cap length to avoid abuse; full IANA validation is best-effort.
        if (value.length() > 64) {
            return "UTC";
        }
        return value;
    }

    /**
     * Loads only the account facts needed for deterministic feed eligibility decisions.
     * @param userId account whose current security signals should be read
     * @return anonymous signals for a missing id/account or the account's TOTP/passkey signals
     */
    private UserSignals loadSignals(Long userId) {
        if (userId == null) {
            return UserSignals.anonymous();
        }
        UserDataBase user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return UserSignals.anonymous();
        }
        boolean totp = user.getTOTPSecret() != null && !user.getTOTPSecret().isBlank();
        int bucket = (int) Math.floorMod(userId, 3L);
        boolean hasPasskey = !passkeyGateway.findByUserId(userId).isEmpty()
                || Boolean.TRUE.equals(user.getPasskeyEnabledForTransactions());
        return new UserSignals(true, totp, hasPasskey, bucket);
    }

    /**
     * Maps accepted balance-view aliases to PLATFORM, ONCHAIN, or the TOTAL fallback.
     * @param raw client-supplied balance view
     * @return canonical balance-view key
     */
    private static String normalizeBalanceView(String raw) {
        if (raw == null || raw.isBlank()) {
            return "TOTAL";
        }
        String v = raw.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "PLATFORM", "INTERNAL", "KEROSENE" -> "PLATFORM";
            case "ONCHAIN", "ON_CHAIN", "BITCOIN" -> "ONCHAIN";
            default -> "TOTAL";
        };
    }

    /**
     * Selects up to three eligible education cards when the personalized catalog has no match.
     * @param balanceView canonical requested view
     * @param locale normalized locale used to render text
     * @return immutable ordered education-card fallback
     */
    private List<HomeFeedItemDTO> fallback(String balanceView, String locale) {
        return catalog().stream()
                .filter(e -> "EDUCATION".equals(e.kind()))
                .filter(e -> e.balanceViews().isEmpty() || e.balanceViews().contains(balanceView)
                        || e.balanceViews().contains("TOTAL"))
                .sorted(Comparator.comparingInt(CatalogEntry::priority).reversed())
                .limit(3)
                .map(e -> e.toDto(locale))
                .toList();
    }

    /**
     * Seed catalog — product-owned content, no admin UI.
     * Platform education is the three secured-card tiers (same EDUCATION kind),
     * with fee rates and upgrade rules from {@link WalletCardTierCatalog}.
     * @return immutable ordered catalog of educational cards, promotions, and announcements
     */
    private List<CatalogEntry> catalog() {
        List<CatalogEntry> entries = new ArrayList<>();

        // ── PLATFORM education: exactly 3 tier cards (fees + rules) ──
        // Highest priority so they win over promos on PLATFORM/TOTAL.
        for (WalletCardTierCatalog.Tier tier : walletCardTierCatalog.tiers()) {
            entries.add(cardTierEducation(tier));
        }

        // ── ONCHAIN network education ─────────────────────────────────
        entries.add(edu("edu-onchain-basics", 100, List.of("ONCHAIN"),
                icon("bitcoin"),
                t("pt", "Bitcoin on-chain",
                        "Use on-chain para guardar valor, autocustódia ou enviar a carteiras externas.",
                        "REDE PRINCIPAL"),
                t("en", "Bitcoin on-chain",
                        "Use on-chain to hold value, self-custody, or send to external wallets.",
                        "MAINNET"),
                t("es", "Bitcoin on-chain",
                        "Usa on-chain para guardar valor, autocustodia o enviar a billeteras externas.",
                        "RED PRINCIPAL")));
        entries.add(edu("edu-confirmations", 95, List.of("ONCHAIN"),
                icon("sync"),
                t("pt", "Confirmações",
                        "Cada bloco reforça a finalização. Em geral 6 confirmações são consideradas seguras.",
                        "SEGURANÇA"),
                t("en", "Confirmations",
                        "Each block strengthens finality. Six confirmations are usually considered safe.",
                        "SECURITY"),
                t("es", "Confirmaciones",
                        "Cada bloque refuerza la finalidad. Seis confirmaciones suelen ser seguras.",
                        "SEGURIDAD")));
        entries.add(edu("edu-fees", 90, List.of("ONCHAIN"),
                icon("gauge"),
                t("pt", "Taxas de rede",
                        "A taxa sobe quando a mempool está cheia. Priorize urgência vs economia.",
                        "CUSTO"),
                t("en", "Network fees",
                        "Fees rise when the mempool is full. Balance urgency versus cost.",
                        "COST"),
                t("es", "Comisiones de red",
                        "La comisión sube cuando la mempool está llena. Equilibra urgencia y costo.",
                        "COSTO")));

        // ── Personalized security promos ──────────────────────────────
        entries.add(promo("promo-enable-passkey", 120, List.of("PLATFORM", "ONCHAIN", "TOTAL"),
                true, false, false,
                icon("biometric"),
                t("pt", "Ative a biometria",
                        "Registre uma passkey neste aparelho para confirmar envios com mais segurança.",
                        "SEGURANÇA"),
                t("en", "Enable biometrics",
                        "Register a passkey on this device to confirm sends more securely.",
                        "SECURITY"),
                t("es", "Activa la biometría",
                        "Registra una passkey en este dispositivo para confirmar envíos con más seguridad.",
                        "SEGURIDAD"),
                cta("Configurar", "NAVIGATE", "/settings/security")));
        entries.add(promo("promo-enable-totp", 115, List.of("PLATFORM", "ONCHAIN", "TOTAL"),
                false, true, false,
                icon("verified"),
                t("pt", "Ative o 2FA",
                        "O TOTP protege login e códigos de backup. Leva menos de um minuto.",
                        "CONTA"),
                t("en", "Turn on 2FA",
                        "TOTP protects login and backup codes. It takes under a minute.",
                        "ACCOUNT"),
                t("es", "Activa el 2FA",
                        "TOTP protege el acceso y los códigos de respaldo. Tarda menos de un minuto.",
                        "CUENTA"),
                cta("Ativar", "NAVIGATE", "/settings/security")));
        entries.add(announcement("ann-network-hint", 60, List.of("ONCHAIN"),
                icon("info"),
                t("pt", "Dica de rede",
                        "Transações on-chain dependem da rede Bitcoin. Acompanhe confirmações no cartão da home.",
                        "AVISO"),
                t("en", "Network tip",
                        "On-chain transfers depend on Bitcoin. Track confirmations on the home cards.",
                        "NOTICE"),
                t("es", "Consejo de red",
                        "Las transferencias on-chain dependen de Bitcoin. Sigue las confirmaciones en inicio.",
                        "AVISO")));

        return List.copyOf(entries);
    }

    /**
     * Builds a localized education card from a secured-card tier's live fee and qualification rules.
     * @param tier current tier policy from the catalog
     * @return education entry available for PLATFORM and TOTAL views
     */
    private CatalogEntry cardTierEducation(WalletCardTierCatalog.Tier tier) {
        String fee = tier.formatFeePercent();
        String volume = tier.formatVolume();
        int months = tier.minAccountMonths();
        String code = tier.code();
        String id = "edu-card-" + code.toLowerCase(Locale.ROOT);
        // Same kind for all three so the education carousel is homogeneous.
        // Client renders a live 3D tier card from the tag — do not ship mock PNGs.
        return edu(
                id,
                tier.priority(),
                List.of("PLATFORM", "TOTAL"),
                icon("creditCard"),
                t("pt",
                        titlePt(code),
                        bodyPt(code, fee, months, volume),
                        code),
                t("en",
                        titleEn(code),
                        bodyEn(code, fee, months, volume),
                        code),
                t("es",
                        titleEs(code),
                        bodyEs(code, fee, months, volume),
                        code));
    }

    /**
     * Resolves a card tier code to its Portuguese display title.
     * @param code tier identifier
     * @return localized title, defaulting to Bronze for other codes
     */
    private static String titlePt(String code) {
        return switch (code) {
            case "WHITE" -> "Cartão White";
            case "BLACK" -> "Cartão Black";
            default -> "Cartão Bronze";
        };
    }

    /**
     * Resolves a card tier code to its English display title.
     * @param code tier identifier
     * @return localized title, defaulting to Bronze for other codes
     */
    private static String titleEn(String code) {
        return switch (code) {
            case "WHITE" -> "White card";
            case "BLACK" -> "Black card";
            default -> "Bronze card";
        };
    }

    /**
     * Resolves a card tier code to its Spanish display title.
     * @param code tier identifier
     * @return localized title, defaulting to Bronze for other codes
     */
    private static String titleEs(String code) {
        return switch (code) {
            case "WHITE" -> "Tarjeta White";
            case "BLACK" -> "Tarjeta Black";
            default -> "Tarjeta Bronze";
        };
    }

    /**
     * Formats the Portuguese tier explanation with fee, account age, and volume qualification values.
     * @param code tier identifier
     * @param fee formatted external fee percentage
     * @param months minimum account age in months
     * @param volume formatted monthly volume threshold
     * @return localized explanatory body for the selected tier
     */
    private static String bodyPt(String code, String fee, int months, String volume) {
        return switch (code) {
            case "WHITE" ->
                    "Taxa de saque/depósito externo: " + fee
                            + ". Como conseguir: movimentação mensal acima de " + volume
                            + " e pelo menos " + months
                            + " meses de conta. O cartão sobe automaticamente quando a conta atinge as regras.";
            case "BLACK" ->
                    "Menor taxa da plataforma: " + fee
                            + " em saques e depósitos externos. Como conseguir: movimentação mensal acima de "
                            + volume + " e pelo menos " + months
                            + " meses de conta. Transferências internas entre usuários Kerosene continuam 0%.";
            default ->
                    "Nível inicial da Conta Assegurada. Taxa externa: " + fee
                            + ". Disponível automaticamente para contas novas. Use a plataforma e aumente o tempo "
                            + "e a movimentação mensal para subir de nível e pagar menos.";
        };
    }

    /**
     * Formats the English tier explanation with fee, account age, and volume qualification values.
     * @param code tier identifier
     * @param fee formatted external fee percentage
     * @param months minimum account age in months
     * @param volume formatted monthly volume threshold
     * @return localized explanatory body for the selected tier
     */
    private static String bodyEn(String code, String fee, int months, String volume) {
        return switch (code) {
            case "WHITE" ->
                    "External deposit/withdrawal fee: " + fee
                            + ". How to unlock: monthly volume above " + volume
                            + " and at least " + months
                            + " months of account age. The card upgrades automatically when rules are met.";
            case "BLACK" ->
                    "Lowest platform fee: " + fee
                            + " on external deposits and withdrawals. How to unlock: monthly volume above "
                            + volume + " and at least " + months
                            + " months of account age. Internal Kerosene transfers stay 0%.";
            default ->
                    "Entry secured-account tier. External fee: " + fee
                            + ". Granted automatically to new accounts. Stay active longer and grow monthly "
                            + "volume to unlock lower tiers.";
        };
    }

    /**
     * Formats the Spanish tier explanation with fee, account age, and volume qualification values.
     * @param code tier identifier
     * @param fee formatted external fee percentage
     * @param months minimum account age in months
     * @param volume formatted monthly volume threshold
     * @return localized explanatory body for the selected tier
     */
    private static String bodyEs(String code, String fee, int months, String volume) {
        return switch (code) {
            case "WHITE" ->
                    "Comisión de depósito/retiro externo: " + fee
                            + ". Cómo conseguirlo: volumen mensual superior a " + volume
                            + " y al menos " + months
                            + " meses de cuenta. La tarjeta sube automáticamente al cumplir las reglas.";
            case "BLACK" ->
                    "Menor comisión de la plataforma: " + fee
                            + " en depósitos y retiros externos. Cómo conseguirlo: volumen mensual superior a "
                            + volume + " y al menos " + months
                            + " meses de cuenta. Las transferencias internas Kerosene siguen en 0%.";
            default ->
                    "Nivel inicial de la cuenta asegurada. Comisión externa: " + fee
                            + ". Disponible automáticamente para cuentas nuevas. Gana antigüedad y volumen "
                            + "mensual para subir de nivel y pagar menos.";
        };
    }

    // ── catalog helpers ────────────────────────────────────────────────────

    /**
     * Creates an education catalog entry that is not gated on account security signals.
     * @param id stable card identifier
     * @param priority selection order weight
     * @param views balance views where the card is eligible
     * @param media card visual
     * @param locales localized card text
     * @return catalog entry of kind EDUCATION
     */
    private static CatalogEntry edu(
            String id,
            int priority,
            List<String> views,
            HomeFeedMediaDTO media,
            Localized... locales) {
        return new CatalogEntry(id, "EDUCATION", priority, views, false, false, false, media, null, locales);
    }

    /**
     * Creates a PROMO entry with explicit passkey/TOTP eligibility gates.
     * @param id stable card identifier
     * @param priority selection order weight
     * @param views eligible balance views
     * @param onlyWithoutPasskey show only when no passkey is available
     * @param onlyWithoutTotp show only when TOTP is not configured
     * @param onlyWithPasskey show only when a passkey is available
     * @param media card visual
     * @param pt Portuguese card text
     * @param en English card text
     * @param es Spanish card text
     * @param cta optional navigation or action
     * @return gated catalog entry of kind PROMO
     */
    private static CatalogEntry promo(
            String id,
            int priority,
            List<String> views,
            boolean onlyWithoutPasskey,
            boolean onlyWithoutTotp,
            boolean onlyWithPasskey,
            HomeFeedMediaDTO media,
            Localized pt,
            Localized en,
            Localized es,
            HomeFeedCtaDTO cta) {
        return new CatalogEntry(
                id, "PROMO", priority, views,
                onlyWithoutPasskey, onlyWithoutTotp, onlyWithPasskey,
                media, cta, pt, en, es);
    }

    /**
     * Creates an ANNOUNCEMENT entry that is not subject to the promotion cap.
     * @param id stable card identifier
     * @param priority selection order weight
     * @param views eligible balance views
     * @param media card visual
     * @param locales localized announcement text
     * @return catalog entry of kind ANNOUNCEMENT
     */
    private static CatalogEntry announcement(
            String id,
            int priority,
            List<String> views,
            HomeFeedMediaDTO media,
            Localized... locales) {
        return new CatalogEntry(id, "ANNOUNCEMENT", priority, views, false, false, false, media, null, locales);
    }

    /**
     * Creates an icon-only media descriptor.
     * @param key symbolic icon key resolved by the client
     * @return square icon media payload
     */
    private static HomeFeedMediaDTO icon(String key) {
        return new HomeFeedMediaDTO("ICON", key, null, null, 1.0);
    }

    /**
     * Creates a card-image descriptor with one URL serving as both image and poster.
     * @param assetOrUrl image resource URL
     * @param aspectRatio preferred image ratio
     * @return image media payload
     */
    private static HomeFeedMediaDTO image(String assetOrUrl, double aspectRatio) {
        return new HomeFeedMediaDTO("IMAGE", "creditCard", assetOrUrl, assetOrUrl, aspectRatio);
    }

    /**
     * Creates a card call-to-action descriptor.
     * @param label user-visible action label
     * @param action client action kind
     * @param target route or destination
     * @return CTA payload
     */
    private static HomeFeedCtaDTO cta(String label, String action, String target) {
        return new HomeFeedCtaDTO(label, action, target);
    }

    /**
     * Creates one localized card-text variant.
     * @param locale language key
     * @param title localized heading
     * @param body localized explanatory text
     * @param tag localized category badge
     * @return locale-specific card content
     */
    private static Localized t(String locale, String title, String body, String tag) {
        return new Localized(locale, title, body, tag);
    }

    /**
     * One translated representation of a catalog card.
     * @param locale language key used for exact locale lookup
     * @param title card heading
     * @param body card description
     * @param tag category badge
     */
    private record Localized(String locale, String title, String body, String tag) {
    }

    /**
     * Minimal user facts used to evaluate catalog promotion conditions.
     * @param authenticated whether an account record was found
     * @param totp whether a TOTP seed is configured
     * @param passkey whether a passkey is registered or enabled for transactions
     * @param bucket stable modulo-three account cohort used for deterministic personalization
     */
    private record UserSignals(boolean authenticated, boolean totp, boolean passkey, int bucket) {
        /** Supplies safe false-valued signals for anonymous or missing accounts. */
        /** @return anonymous signal set with neutral bucket */
        static UserSignals anonymous() {
            return new UserSignals(false, false, false, 0);
        }
    }

    /**
     * Seed catalog item together with audience, factor, media, CTA, and translation rules.
     * @param id stable item identifier
     * @param kind card category, such as EDUCATION, PROMO, or ANNOUNCEMENT
     * @param priority descending selection weight
     * @param balanceViews canonical balance views where the item may appear
     * @param onlyWithoutPasskey passkey absence gate
     * @param onlyWithoutTotp TOTP absence gate
     * @param onlyWithPasskey passkey presence gate
     * @param media visual descriptor
     * @param cta optional user action
     * @param locales localized text variants; first value is the ultimate fallback
     */
    private record CatalogEntry(
            String id,
            String kind,
            int priority,
            List<String> balanceViews,
            boolean onlyWithoutPasskey,
            boolean onlyWithoutTotp,
            boolean onlyWithPasskey,
            HomeFeedMediaDTO media,
            HomeFeedCtaDTO cta,
            Localized... locales) {

        /** Evaluates the item's view and security-factor eligibility rules. */
        /** @param balanceView normalized balance view requested by the client */
        /** @param locale normalized locale key */
        /** @param signals account-level eligibility facts */
        /** @return true when the item passes view, factor, and translation checks */
        boolean matches(String balanceView, String locale, UserSignals signals) {
            if (!balanceViews.isEmpty()
                    && !balanceViews.contains(balanceView)
                    && !balanceViews.contains("TOTAL")
                    && !"TOTAL".equals(balanceView)) {
                // allow TOTAL items when view is TOTAL; for specific views require match
            }
            if (!balanceViews.isEmpty() && !balanceViews.contains(balanceView)) {
                return false;
            }
            if (onlyWithoutPasskey && signals.passkey()) {
                return false;
            }
            if (onlyWithoutTotp && signals.totp()) {
                return false;
            }
            if (onlyWithPasskey && !signals.passkey()) {
                return false;
            }
            // Bucket personalization: promo/feature with id hash can skip some users
            if (("PROMO".equals(kind) || "FEATURE".equals(kind))
                    && signals.authenticated()
                    && Math.floorMod(id.hashCode() + signals.bucket(), 2) == 0
                    && onlyWithoutPasskey) {
                // still show passkey promo when needed
            }
            return localeOf(locale) != null;
        }

        /** Converts the selected localized entry into the response card shape. */
        /** @param locale requested normalized locale */
        /** @return card DTO using locale fallback and semantic surface tint */
        HomeFeedItemDTO toDto(String locale) {
            Localized l = Objects.requireNonNullElse(localeOf(locale), locales[0]);
            String tint = balanceViews.contains("ONCHAIN")
                    ? "ONCHAIN"
                    : balanceViews.contains("PLATFORM") ? "PLATFORM" : "TOTAL";
            return new HomeFeedItemDTO(
                    id,
                    kind,
                    priority,
                    "MEDIA_LEFT",
                    l.title(),
                    l.body(),
                    l.tag(),
                    media,
                    cta,
                    tint,
                    null,
                    id);
        }

        /** Chooses an exact locale, then Portuguese, then the first catalog translation. */
        /** @param locale requested language key */
        /** @return best available translation, or null for an empty translation list */
        private Localized localeOf(String locale) {
            for (Localized l : locales) {
                if (l.locale().equals(locale)) {
                    return l;
                }
            }
            for (Localized l : locales) {
                if ("pt".equals(l.locale())) {
                    return l;
                }
            }
            return locales.length > 0 ? locales[0] : null;
        }
    }
}
