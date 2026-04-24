package ru.otus.study.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class AuthHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {

        UUID userId = (UUID) attributes.get("userId");
        if (userId != null) {
            return new StompPrincipal(userId);
        }
        return null;
    }

    public record StompPrincipal(UUID userId) implements Principal {
        @Override
        public String getName() {
            return userId.toString();
        }
    }
}