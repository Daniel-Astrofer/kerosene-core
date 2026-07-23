package com.kerosene.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class HttpClientConfig {

    private static final Logger log = LoggerFactory.getLogger(HttpClientConfig.class);

    @Bean("esploraRestTemplate")
    public RestTemplate esploraRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Bean("custodyRestTemplate")
    public RestTemplate custodyRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Bean("btcpayRestTemplate")
    public RestTemplate btcpayRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Bean("bitcoindRestTemplate")
    public RestTemplate bitcoindRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Bean("lightningRestTemplate")
    public RestTemplate lightningRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Bean("lndRestTemplate")
    public RestTemplate lndRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

    /**
     * Market-price HTTP client.
     *
     * <p>When {@code ticker.proxy.socks-host} is set, all CoinGecko traffic is
     * routed through Tor SOCKS5 so the app pod never needs clearnet egress.
     * Timeouts are longer on the SOCKS path because Tor circuit setup is slow.
     */
    @Bean("tickerRestTemplate")
    public RestTemplate tickerRestTemplate(
            RestTemplateBuilder builder,
            @Value("${ticker.proxy.socks-host:}") String socksHost,
            @Value("${ticker.proxy.socks-port:9050}") int socksPort) {
        if (socksHost != null && !socksHost.isBlank()) {
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(socksHost.trim(), socksPort));
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setProxy(proxy);
            // Tor circuits routinely need tens of seconds to establish.
            factory.setConnectTimeout(Duration.ofSeconds(45));
            factory.setReadTimeout(Duration.ofSeconds(60));
            log.info(
                    "[Ticker] RestTemplate using Tor SOCKS5 proxy {}:{} (no clearnet egress)",
                    socksHost.trim(),
                    socksPort);
            return new RestTemplate(factory);
        }

        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Bean("mempoolHttpClient")
    public HttpClient mempoolHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
}
