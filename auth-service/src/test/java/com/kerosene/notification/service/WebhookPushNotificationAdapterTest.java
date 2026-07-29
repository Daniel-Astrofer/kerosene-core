package com.kerosene.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import com.kerosene.notification.model.entity.NotificationDeviceTokenEntity;
import com.kerosene.notification.repository.NotificationDeviceTokenRepository;

class WebhookPushNotificationAdapterTest {

    private final NotificationDeviceTokenRepository repository = mock(NotificationDeviceTokenRepository.class);
    private final NotificationDeviceTokenService tokenService = mock(NotificationDeviceTokenService.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final RestTemplateBuilder restTemplateBuilder = mock(RestTemplateBuilder.class);

    private WebhookPushNotificationAdapter adapter;

    @BeforeEach
    void setUp() {
        when(restTemplateBuilder.setConnectTimeout(any(java.time.Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any(java.time.Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);

        adapter = new WebhookPushNotificationAdapter(
                repository,
                tokenService,
                restTemplateBuilder,
                new ObjectMapper(),
                "https://relay.example/push",
                "secret",
                1000,
                1000);
    }

    @Test
    void skipsLocalAlertTokens() {
        NotificationDeviceTokenEntity local = new NotificationDeviceTokenEntity();
        local.setPlatform("ANDROID");
        local.setTokenRef("sha256:abc");
        when(repository.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(7L))
                .thenReturn(List.of(local));
        when(tokenService.decryptTokenBestEffort(local)).thenReturn("local-alert:install-1");

        adapter.dispatch(7L, Map.of("kind", "deposit_detected", "title", "Depósito"));

        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(Void.class));
    }

    @Test
    void postsRemoteTokens() {
        NotificationDeviceTokenEntity remote = new NotificationDeviceTokenEntity();
        remote.setPlatform("ANDROID");
        remote.setTokenRef("sha256:fcm");
        remote.setAppVersion("1.0.0");
        when(repository.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(7L))
                .thenReturn(List.of(remote));
        when(tokenService.decryptTokenBestEffort(remote))
                .thenReturn("fcm-token-value-long-enough-to-be-remote-capable-0123456789");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.accepted().build());

        adapter.dispatch(
                7L,
                Map.of(
                        "kind", "deposit_detected",
                        "title", "Depósito",
                        "body", "0.001 BTC"));

        verify(restTemplate).postForEntity(eq("https://relay.example/push"), any(HttpEntity.class), eq(Void.class));
    }
}
