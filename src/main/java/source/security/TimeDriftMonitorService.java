package source.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class TimeDriftMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(TimeDriftMonitorService.class);

    // Oráculos de Tempo Descentralizados Baseados em HTTPS (Previne NTP UDP
    // Spoofing)
    private static final String[] HTTPS_TIME_ORACLES = {
            "https://www.google.com",
            "https://cloudflare.com",
            "https://www.apple.com"
    };

    private static final long MAX_DRIFT_THRESHOLD_MS = 5000; // Tolerância de 5 segundos

    private final RemoteAttestationService attestationService;
    private final HttpClient httpClient;

    public TimeDriftMonitorService(RemoteAttestationService attestationService) {
        this.attestationService = attestationService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Scheduled(fixedRate = 300000) // Verificação a cada 5 minutos
    public void monitorTimeDrift() {
        List<Long> offsets = new ArrayList<>();

        for (String server : HTTPS_TIME_ORACLES) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(server))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(5))
                        .build();

                Instant before = Instant.now();
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                Instant after = Instant.now();

                long rtt = Duration.between(before, after).toMillis();
                String dateHeader = response.headers().firstValue("Date").orElse(null);

                if (dateHeader != null) {
                    ZonedDateTime serverTime = ZonedDateTime.parse(dateHeader, DateTimeFormatter.RFC_1123_DATE_TIME);
                    long serverEpoch = serverTime.toInstant().toEpochMilli();
                    long localEpoch = before.toEpochMilli() + (rtt / 2);

                    long offset = serverEpoch - localEpoch;
                    offsets.add(offset);
                }
            } catch (Exception e) {
                logger.warn("Falha ao contatar oráculo HTTPS " + server + ": " + e.getMessage());
            }
        }

        if (offsets.isEmpty()) {
            logger.warn("Nenhum oráculo HTTPS alcançável. Assumindo risco operacional de relógio cego.");
            return;
        }

        long medianOffset = calculateMedian(offsets);

        logger.info("[TimeDriftMonitor] Desvio atual em relação ao tempo HTTPS global: {} ms", medianOffset);

        if (Math.abs(medianOffset) > MAX_DRIFT_THRESHOLD_MS) {
            logger.error("🚨 [ALERTA CRÍTICO DE SEGURANÇA] Desvio de tempo severo detectado ({} ms). " +
                    "Possivel ataque de NTP Spoofing / Manipulação de Relógio Host. " +
                    "Isso comprometeria o mTLS e os tokens TOTP.", medianOffset);

            // Força o Stall Mode defensivamente para proteger os ativos
            triggerStallMode();
        }
    }

    private long calculateMedian(List<Long> values) {
        values.sort(Long::compareTo);
        if (values.size() % 2 == 0) {
            return (values.get(values.size() / 2 - 1) + values.get(values.size() / 2)) / 2;
        } else {
            return values.get(values.size() / 2);
        }
    }

    private void triggerStallMode() {
        attestationService.invalidateAttestation("HTTPS_TIME_DRIFT_ANOMALY");
    }
}
