package source.transactions.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import source.transactions.dto.PaymentLinkDTO;
import source.ledger.dto.InternalPaymentRequestDTO;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, PaymentLinkDTO> redisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {
        RedisTemplate<String, PaymentLinkDTO> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        ObjectMapper redisObjectMapper = objectMapper.copy().registerModule(new JavaTimeModule());

        // Usar Jackson2JsonRedisSerializer com o ObjectMapper configurado
        Jackson2JsonRedisSerializer<PaymentLinkDTO> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(
                redisObjectMapper, PaymentLinkDTO.class);

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // Configurar serialização
        template.setKeySerializer(stringRedisSerializer);
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisTemplate<String, InternalPaymentRequestDTO> internalPaymentRequestRedisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {
        RedisTemplate<String, InternalPaymentRequestDTO> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        ObjectMapper redisObjectMapper = objectMapper.copy().registerModule(new JavaTimeModule());

        Jackson2JsonRedisSerializer<InternalPaymentRequestDTO> serializer = new Jackson2JsonRedisSerializer<>(
                redisObjectMapper, InternalPaymentRequestDTO.class);

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringRedisSerializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(stringRedisSerializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}
