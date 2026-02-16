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

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, PaymentLinkDTO> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, PaymentLinkDTO> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Configurar ObjectMapper com suporte a Java 8 Date/Time
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Usar Jackson2JsonRedisSerializer com o ObjectMapper configurado
        Jackson2JsonRedisSerializer<PaymentLinkDTO> jackson2JsonRedisSerializer = 
            new Jackson2JsonRedisSerializer<>(PaymentLinkDTO.class);
        jackson2JsonRedisSerializer.setObjectMapper(objectMapper);

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // Configurar serialização
        template.setKeySerializer(stringRedisSerializer);
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
