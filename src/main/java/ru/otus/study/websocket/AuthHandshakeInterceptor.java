package ru.otus.study.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import ru.otus.study.service.JwtService;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;

            // Извлекаем токен из параметров запроса или заголовка
            String token = servletRequest.getServletRequest().getParameter("token");
            if (token == null) {
                String authHeader = servletRequest.getServletRequest().getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }

            if (token != null) {
                try {
                    UUID userId = jwtService.extractUserId(token);
                    attributes.put("userId", userId);
                    attributes.put("token", token);
                    log.debug("WebSocket handshake successful for user {}", userId);
                    return true;
                } catch (Exception e) {
                    log.error("Invalid token during WebSocket handshake: {}", e.getMessage());
                    return false;
                }
            }
        }

        log.warn("No token provided for WebSocket connection");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // Nothing to do
    }
}