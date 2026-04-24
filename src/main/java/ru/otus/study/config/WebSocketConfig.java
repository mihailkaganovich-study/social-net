package ru.otus.study.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import ru.otus.study.websocket.AuthHandshakeHandler;
import ru.otus.study.websocket.AuthHandshakeInterceptor;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ObjectMapper objectMapper;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;
    private final AuthHandshakeHandler authHandshakeHandler;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Включаем простой брокер для отправки сообщений клиентам
        registry.enableSimpleBroker("/post", "/queue");

        // Префикс для сообщений от клиента к серверу
        registry.setApplicationDestinationPrefixes("/app");

        // Префикс для пользовательских очередей
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(authHandshakeInterceptor)
                .setHandshakeHandler(authHandshakeHandler)
                .withSockJS();
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(8192);
        registration.setSendBufferSizeLimit(512 * 1024);
        registration.setSendTimeLimit(20000);
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
        resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);

        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        converter.setContentTypeResolver(resolver);

        messageConverters.add(converter);
        return false;
    }
}