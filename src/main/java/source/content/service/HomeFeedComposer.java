package source.content.service;

import org.springframework.stereotype.Service;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.port.out.AuthPasskeyGateway;
import source.auth.model.entity.UserDataBase;
import source.content.dto.HomeFeedCtaDTO;
import source.content.dto.HomeFeedItemDTO;
import source.content.dto.HomeFeedMediaDTO;
import source.content.dto.HomeFeedResponseDTO;

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

    private static final int MAX_ITEMS = 8;
    /** Cap paid/security promos only — FEATURE and ANNOUNCEMENT cards can still fill the feed. */
    private static final int MAX_PROMOS = 2;
    private static final int TTL_SECONDS = 300;

    private final UserRepository userRepository;
    private final AuthPasskeyGateway passkeyGateway;

    public HomeFeedComposer(UserRepository userRepository, AuthPasskeyGateway passkeyGateway) {
        this.userRepository = userRepository;
        this.passkeyGateway = passkeyGateway;
    }

    public HomeFeedResponseDTO compose(
            Long userId,
            String balanceViewRaw,
            String localeRaw) {
        return compose(userId, balanceViewRaw, localeRaw, null);
    }

    public HomeFeedResponseDTO compose(
            Long userId,
            String balanceViewRaw,
            String localeRaw,
            String timeZoneRaw) {
        String balanceView = normalizeBalanceView(balanceViewRaw);
        String locale = normalizeLocale(localeRaw);
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

    private static String normalizeLocale(String raw) {
        if (raw == null || raw.isBlank()) {
            return "pt";
        }
        String lang = raw.trim().toLowerCase(Locale.ROOT);
        if (lang.startsWith("en")) {
            return "en";
        }
        if (lang.startsWith("es")) {
            return "es";
        }
        return "pt";
    }

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
     * Personalization via rules on each entry.
     */
    private List<CatalogEntry> catalog() {
        return List.of(
                // ── PLATFORM ──────────────────────────────────────────────
                edu("edu-internal-p2p", 100, List.of("PLATFORM", "TOTAL"),
                        icon("internalTransfer"),
                        t("pt", "Transferências internas",
                                "Envie valor entre contas Kerosene sem taxa de rede Bitcoin.",
                                "PLATAFORMA"),
                        t("en", "Internal transfers",
                                "Move value between Kerosene accounts without Bitcoin network fees.",
                                "PLATFORM"),
                        t("es", "Transferencias internas",
                                "Mueve valor entre cuentas Kerosene sin comisiones de red Bitcoin.",
                                "PLATAFORMA")),
                edu("edu-wallet-hash", 90, List.of("PLATFORM"),
                        icon("biometric"),
                        t("pt", "Hash da carteira",
                                "Seu identificador interno protege a privacidade ao receber de outros usuários.",
                                "PRIVACIDADE"),
                        t("en", "Wallet handle",
                                "Your internal handle keeps privacy when receiving from other users.",
                                "PRIVACY"),
                        t("es", "Identificador de billetera",
                                "Tu identificador interno protege la privacidad al recibir de otros.",
                                "PRIVACIDAD")),
                edu("edu-lightning-platform", 80, List.of("PLATFORM", "TOTAL"),
                        icon("lightning"),
                        t("pt", "Lightning na plataforma",
                                "Pagamentos instantâneos quando ambos os lados estão na rede Lightning.",
                                "VELOCIDADE"),
                        t("en", "Lightning on platform",
                                "Instant payments when both sides are on Lightning.",
                                "SPEED"),
                        t("es", "Lightning en la plataforma",
                                "Pagos instantáneos cuando ambos lados usan Lightning.",
                                "VELOCIDAD")),

                // ── ONCHAIN ───────────────────────────────────────────────
                edu("edu-onchain-basics", 100, List.of("ONCHAIN", "TOTAL"),
                        icon("bitcoin"),
                        t("pt", "Bitcoin on-chain",
                                "Use on-chain para guardar valor, autocustódia ou enviar a carteiras externas.",
                                "REDE PRINCIPAL"),
                        t("en", "Bitcoin on-chain",
                                "Use on-chain to hold value, self-custody, or send to external wallets.",
                                "MAINNET"),
                        t("es", "Bitcoin on-chain",
                                "Usa on-chain para guardar valor, autocustodia o enviar a billeteras externas.",
                                "RED PRINCIPAL")),
                edu("edu-confirmations", 95, List.of("ONCHAIN"),
                        icon("sync"),
                        t("pt", "Confirmações",
                                "Cada bloco reforça a finalização. Em geral 6 confirmações são consideradas seguras.",
                                "SEGURANÇA"),
                        t("en", "Confirmations",
                                "Each block strengthens finality. Six confirmations are usually considered safe.",
                                "SECURITY"),
                        t("es", "Confirmaciones",
                                "Cada bloque refuerza la finalidad. Seis confirmaciones suelen ser seguras.",
                                "SEGURIDAD")),
                edu("edu-fees", 90, List.of("ONCHAIN"),
                        icon("gauge"),
                        t("pt", "Taxas de rede",
                                "A taxa sobe quando a mempool está cheia. Priorize urgência vs economia.",
                                "CUSTO"),
                        t("en", "Network fees",
                                "Fees rise when the mempool is full. Balance urgency versus cost.",
                                "COST"),
                        t("es", "Comisiones de red",
                                "La comisión sube cuando la mempool está llena. Equilibra urgencia y costo.",
                                "COSTO")),

                // ── TOTAL / general ────────────────────────────────────────
                edu("edu-bitcoin-general", 70, List.of("TOTAL"),
                        icon("bitcoin"),
                        t("pt", "Por que Bitcoin",
                                "Ativo escasso, liquidável globalmente e com liquidação verificável.",
                                "BASE"),
                        t("en", "Why Bitcoin",
                                "Scarce, globally liquid, with verifiable settlement.",
                                "BASICS"),
                        t("es", "Por qué Bitcoin",
                                "Activo escaso, líquido globalmente y con liquidación verificable.",
                                "BASE")),

                // ── Kerosene cards announcement (Bronze / Metal / Gold) ──
                announcement("ann-kerosene-cards-trio", 130, List.of("PLATFORM", "ONCHAIN", "TOTAL"),
                        image("asset:assets/feed/cards/trio.png", 2.0),
                        t("pt", "Cartões Kerosene",
                                "Três níveis: Bronze, Metal e Gold. Cada cartão assegurado com taxas e aparência próprias.",
                                "CARTÕES"),
                        t("en", "Kerosene cards",
                                "Three tiers: Bronze, Metal and Gold. Each secured card has its own look and fee profile.",
                                "CARDS"),
                        t("es", "Tarjetas Kerosene",
                                "Tres niveles: Bronze, Metal y Gold. Cada tarjeta tiene estilo y comisiones propios.",
                                "TARJETAS")),
                feature("feat-card-bronze", 125, List.of("PLATFORM", "TOTAL"),
                        image("asset:assets/feed/cards/bronze.png", 1.6),
                        t("pt", "Cartão Bronze",
                                "Entrada na Conta Assegurada. Ideal para começar com transferências internas e on-chain.",
                                "BRONZE"),
                        t("en", "Bronze card",
                                "Entry secured account. Great for starting with internal and on-chain transfers.",
                                "BRONZE"),
                        t("es", "Tarjeta Bronze",
                                "Cuenta asegurada de entrada. Ideal para empezar con transferencias internas y on-chain.",
                                "BRONZE")),
                feature("feat-card-metal", 124, List.of("PLATFORM", "TOTAL"),
                        image("asset:assets/feed/cards/metal.png", 1.6),
                        t("pt", "Cartão Metal",
                                "Acabamento metálico premium. Taxas mais competitivas que o Bronze para uso frequente.",
                                "METAL"),
                        t("en", "Metal card",
                                "Premium metallic finish. More competitive fees than Bronze for frequent use.",
                                "METAL"),
                        t("es", "Tarjeta Metal",
                                "Acabado metálico premium. Comisiones más competitivas que Bronze para uso frecuente.",
                                "METAL")),
                feature("feat-card-gold", 123, List.of("PLATFORM", "TOTAL"),
                        image("asset:assets/feed/cards/gold.png", 1.6),
                        t("pt", "Cartão Gold",
                                "Topo de linha: visual escuro com detalhes dourados e as menores taxas da linha assegurada.",
                                "GOLD"),
                        t("en", "Gold card",
                                "Top tier: dark look with gold details and the lowest secured-card fees.",
                                "GOLD"),
                        t("es", "Tarjeta Gold",
                                "Nivel superior: look oscuro con detalles dorados y las comisiones más bajas.",
                                "GOLD")),

                // ── Personalized promos / announcements (no admin CMS) ───
                promo("promo-enable-passkey", 120, List.of("PLATFORM", "ONCHAIN", "TOTAL"),
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
                        cta("Configurar", "NAVIGATE", "/settings/security")),
                promo("promo-enable-totp", 115, List.of("PLATFORM", "ONCHAIN", "TOTAL"),
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
                        cta("Ativar", "NAVIGATE", "/settings/security")),
                announcement("ann-network-hint", 60, List.of("ONCHAIN", "TOTAL"),
                        icon("info"),
                        t("pt", "Dica de rede",
                                "Transações on-chain dependem da rede Bitcoin. Acompanhe confirmações no cartão da home.",
                                "AVISO"),
                        t("en", "Network tip",
                                "On-chain transfers depend on Bitcoin. Track confirmations on the home cards.",
                                "NOTICE"),
                        t("es", "Consejo de red",
                                "Las transferencias on-chain dependen de Bitcoin. Sigue las confirmaciones en inicio.",
                                "AVISO")),
                // Video-capable slot (URL may be empty until CDN assets exist — client falls back to icon)
                feature("feat-video-welcome", 50, List.of("TOTAL"),
                        video(null, null),
                        t("pt", "Novidades Kerosene",
                                "Em breve: conteúdo em vídeo e animações personalizadas no feed da home.",
                                "NOVIDADE"),
                        t("en", "Kerosene updates",
                                "Coming soon: personalized video and animation content on the home feed.",
                                "NEW"),
                        t("es", "Novedades Kerosene",
                                "Pronto: video y animaciones personalizadas en el feed de inicio.",
                                "NUEVO"))
        );
    }

    // ── catalog helpers ────────────────────────────────────────────────────

    private static CatalogEntry edu(
            String id,
            int priority,
            List<String> views,
            HomeFeedMediaDTO media,
            Localized... locales) {
        return new CatalogEntry(id, "EDUCATION", priority, views, false, false, false, media, null, locales);
    }

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

    private static CatalogEntry announcement(
            String id,
            int priority,
            List<String> views,
            HomeFeedMediaDTO media,
            Localized... locales) {
        return new CatalogEntry(id, "ANNOUNCEMENT", priority, views, false, false, false, media, null, locales);
    }

    private static CatalogEntry feature(
            String id,
            int priority,
            List<String> views,
            HomeFeedMediaDTO media,
            Localized... locales) {
        return new CatalogEntry(id, "FEATURE", priority, views, false, false, false, media, null, locales);
    }

    private static HomeFeedMediaDTO icon(String key) {
        return new HomeFeedMediaDTO("ICON", key, null, null, 1.0);
    }

    private static HomeFeedMediaDTO image(String assetOrUrl, double aspectRatio) {
        return new HomeFeedMediaDTO("IMAGE", "creditCard", assetOrUrl, assetOrUrl, aspectRatio);
    }

    private static HomeFeedMediaDTO video(String url, String poster) {
        return new HomeFeedMediaDTO("VIDEO", "play", url, poster, 16.0 / 9.0);
    }

    private static HomeFeedCtaDTO cta(String label, String action, String target) {
        return new HomeFeedCtaDTO(label, action, target);
    }

    private static Localized t(String locale, String title, String body, String tag) {
        return new Localized(locale, title, body, tag);
    }

    private record Localized(String locale, String title, String body, String tag) {
    }

    private record UserSignals(boolean authenticated, boolean totp, boolean passkey, int bucket) {
        static UserSignals anonymous() {
            return new UserSignals(false, false, false, 0);
        }
    }

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
