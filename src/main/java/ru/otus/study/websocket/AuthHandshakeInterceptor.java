// ru/otus/study/websocket/AuthHandshakeInterceptor.java
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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    // AuthHandshakeInterceptor.java
    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        String token = null;

        // 1. Проверяем заголовок Authorization (наиболее надежный способ)
        List<String> authHeaders = request.getHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.get(0);
            if (authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
                log.debug("Token found in Authorization header");
            }
        }

        // 2. Проверяем query параметры URL
        if (token == null) {
            URI uri = request.getURI();
            if (uri != null && uri.getQuery() != null) {
                String query = uri.getQuery();
                for (String param : query.split("&")) {
                    String[] parts = param.split("=");
                    if (parts.length == 2 && "token".equals(parts[0])) {
                        token = java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                        log.debug("Token found in URL query parameter");
                        break;
                    }
                }
            }
        }

        // 3. Проверяем Servlet параметры
        if (token == null && request instanceof ServletServerHttpRequest servletRequest) {
            token = servletRequest.getServletRequest().getParameter("token");
            if (token != null) {
                log.debug("Token found in servlet parameter");
            }
        }

        // 4. Логируем все заголовки для отладки
        if (token == null) {
            log.warn("No token found. Request headers: {}", request.getHeaders());
            log.warn("Request URI: {}", request.getURI());
        }

        if (token != null) {
            try {
                UUID userId = jwtService.extractUserId(token);
                attributes.put("userId", userId);
                attributes.put("token", token);
                log.info("WebSocket handshake successful for user {}", userId);
                return true;
            } catch (Exception e) {
                log.error("Invalid token: {}", e.getMessage());
            }
        }

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