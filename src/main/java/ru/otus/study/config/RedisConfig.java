package ru.otus.study.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.RedisConnectionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import ru.otus.study.dto.PostDto;

@Slf4j
@Configuration
public class RedisConfig {

    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory(
            @org.springframework.beans.factory.annotation.Value("${spring.redis.host:localhost}") String host,
            @org.springframework.beans.factory.annotation.Value("${spring.redis.port:6379}") int port,
            @org.springframework.beans.factory.annotation.Value("${spring.redis.password:}") String password) {

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(host);
        config.setPort(port);
        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();

        try {
            factory.getConnection().ping();
            log.info("Successfully connected to Redis at {}:{}", host, port);
        } catch (RedisConnectionException e) {
            log.error("Failed to connect to Redis at {}:{} - {}", host, port, e.getMessage());
        }

        return factory;
    }

    @Bean
    public Jackson2JsonRedisSerializer<PostDto> postDtoSerializer(ObjectMapper redisObjectMapper) {
        return new Jackson2JsonRedisSerializer<>(redisObjectMapper, PostDto.class);
    }

    @Bean
    public RedisTemplate<String, PostDto> redisTemplate(
            RedisConnectionFactory connectionFactory,
            Jackson2JsonRedisSerializer<PostDto> postDtoSerializer) {

        RedisTemplate<String, PostDto> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(postDtoSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(postDtoSerializer);
        template.afterPropertiesSet();

        return template;
    }
}