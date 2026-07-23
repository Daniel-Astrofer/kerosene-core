package com.kerosene.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpClientConfigTickerProxyTest {

    @Test
    void tickerRestTemplateUsesSocksWhenHostConfigured() throws Exception {
        HttpClientConfig config = new HttpClientConfig();
        RestTemplate restTemplate = config.tickerRestTemplate(new RestTemplateBuilder(), "tor-onion", 9050);

        assertInstanceOf(SimpleClientHttpRequestFactory.class, restTemplate.getRequestFactory());
        SimpleClientHttpRequestFactory factory =
                (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();
        Object proxy = readField(factory, "proxy");
        assertNotNull(proxy);
        assertTrue(proxy.toString().contains("SOCKS"));
        assertTrue(proxy.toString().contains("tor-onion"));
    }

    @Test
    void tickerRestTemplateSkipsProxyWhenHostBlank() {
        HttpClientConfig config = new HttpClientConfig();
        RestTemplate restTemplate = config.tickerRestTemplate(new RestTemplateBuilder(), "", 9050);
        assertNotNull(restTemplate);
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
