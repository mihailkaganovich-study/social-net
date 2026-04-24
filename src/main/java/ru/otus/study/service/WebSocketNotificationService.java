package ru.otus.study.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;
import ru.otus.study.dto.PostMessage;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;

    /**
     * Отправляет уведомление о новом посте конкретному пользователю
     */
    public void notifyUserAboutNewPost(UUID userId, PostMessage postMessage) {
        try {
            // Отправляем сообщение в пользовательскую очередь
            // Согласно AsyncAPI спецификации, канал /post/feed/posted
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/post/feed/posted",
                    postMessage
            );

            log.debug("Sent post notification to user {} for post {}",
                    userId, postMessage.getPostId());

        } catch (Exception e) {
            log.error("Failed to send WebSocket notification to user {}: {}",
                    userId, e.getMessage());
        }
    }

    /**
     * Проверяет, подключен ли пользователь в данный момент
     */
    public boolean isUserConnected(UUID userId) {
        try {
            SimpUser user = simpUserRegistry.getUser(userId.toString());
            boolean connected = user != null && user.hasSessions();

            if (connected) {
                log.debug("User {} is connected with {} session(s)",
                        userId, user.getSessions().size());
            }

            return connected;
        } catch (Exception e) {
            log.error("Failed to check user connection status for {}: {}", userId, e.getMessage());
            return false;
        }
    }

}