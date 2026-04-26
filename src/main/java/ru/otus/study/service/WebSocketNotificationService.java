package ru.otus.study.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;
import ru.otus.study.dto.PostMessage;

import java.util.UUID;

// ru/otus/study/service/WebSocketNotificationService.java
// Добавим больше логов для отладки

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;

    public void notifyUserAboutNewPost(UUID userId, PostMessage postMessage) {
        try {
            // Проверяем подключен ли пользователь
            boolean connected = isUserConnected(userId);
            log.info("Attempting to send notification to user {} (connected: {}) for post {}",
                    userId, connected, postMessage.getPostId());

            if (!connected) {
                log.info("User {} is not connected, skipping notification", userId);
                return;
            }

            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/post/feed/posted",
                    postMessage
            );

            log.info("Successfully sent post notification to user {} for post {}",
                    userId, postMessage.getPostId());

        } catch (Exception e) {
            log.error("Failed to send WebSocket notification to user {}: {}",
                    userId, e.getMessage(), e);
        }
    }

    public boolean isUserConnected(UUID userId) {
        try {
            SimpUser user = simpUserRegistry.getUser(userId.toString());
            boolean connected = user != null && user.hasSessions();

            if (connected) {
                log.info("User {} is connected with {} session(s)",
                        userId, user.getSessions().size());
            } else {
                log.debug("User {} is NOT connected (user: {}, hasSessions: {})",
                        userId, user != null, user != null ? user.hasSessions() : false);
            }

            return connected;
        } catch (Exception e) {
            log.error("Failed to check user connection status for {}: {}", userId, e.getMessage());
            return false;
        }
    }
}