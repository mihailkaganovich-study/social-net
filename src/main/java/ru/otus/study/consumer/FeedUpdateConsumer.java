package ru.otus.study.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import ru.otus.study.dto.FeedUpdateTask;
import ru.otus.study.dto.PostMessage;
import ru.otus.study.model.Post;
import ru.otus.study.repository.FriendRepository;
import ru.otus.study.repository.PostRepository;
import ru.otus.study.service.FeedCacheService;
import ru.otus.study.service.FeedUpdateProducer;
import ru.otus.study.service.WebSocketNotificationService;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedUpdateConsumer {

    private final PostRepository postRepository;
    private final FriendRepository friendRepository;
    private final FeedCacheService feedCacheService;
    private final WebSocketNotificationService notificationService;
    private final FeedUpdateProducer feedUpdateProducer;

    private static final int FRIENDS_BATCH_SIZE = 100;

    /**
     * Основной обработчик создания поста
     * Здесь происходит ВСЯ логика обработки нового поста
     */
    @KafkaListener(topics = "post.created", groupId = "post-processor", concurrency = "3")
    public void handlePostCreated(PostMessage postMessage, Acknowledgment ack) {
        log.info("Processing post created: {}", postMessage.getPostId());
        long startTime = System.currentTimeMillis();

        try {
            UUID postId = UUID.fromString(postMessage.getPostId());
            UUID authorId = UUID.fromString(postMessage.getAuthorUserId());

            // 1. Получаем пост из БД
            Post post = postRepository.findById(postId);
            if (post == null) {
                log.warn("Post {} not found", postId);
                ack.acknowledge();
                return;
            }

            // 2. Добавляем пост в ленту автора
            feedCacheService.addPostToUserFeed(authorId, post);
            log.debug("Added post {} to author's feed", postId);

            // 3. Получаем список друзей автора
            List<UUID> friendIds = friendRepository.findFriendIds(authorId);

            if (friendIds.isEmpty()) {
                log.info("User {} has no friends, processing completed", authorId);
                ack.acknowledge();
                return;
            }

            log.info("User {} has {} friends, creating update tasks", authorId, friendIds.size());

            // 4. Создаем задачи на обновление лент друзей батчами
            feedUpdateProducer.createFeedUpdateTasks(postId, authorId, friendIds, FRIENDS_BATCH_SIZE);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Post {} processing completed in {} ms, {} friends to update",
                    postId, duration, friendIds.size());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process post created: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process post created", e);
        }
    }

    /**
     * Обработчик обновления поста
     */
    @KafkaListener(topics = "post.updated", groupId = "post-processor")
    public void handlePostUpdated(PostMessage postMessage, Acknowledgment ack) {
        log.info("Processing post updated: {}", postMessage.getPostId());

        try {
            UUID postId = UUID.fromString(postMessage.getPostId());
            UUID authorId = UUID.fromString(postMessage.getAuthorUserId());

            Post post = postRepository.findById(postId);
            if (post == null) {
                log.warn("Post {} not found", postId);
                ack.acknowledge();
                return;
            }

            // Обновляем пост в кеше автора
            feedCacheService.updatePostInUserFeed(authorId, post);

            // Получаем друзей и обновляем их ленты
            List<UUID> friendIds = friendRepository.findFriendIds(authorId);
            if (!friendIds.isEmpty()) {
                feedUpdateProducer.createFeedUpdateTasks(postId, authorId, friendIds, FRIENDS_BATCH_SIZE);
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process post updated: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process post updated", e);
        }
    }

    /**
     * Обработчик удаления поста
     */
    @KafkaListener(topics = "post.deleted", groupId = "post-processor")
    public void handlePostDeleted(PostMessage postMessage, Acknowledgment ack) {
        log.info("Processing post deleted: {}", postMessage.getPostId());

        try {
            UUID postId = UUID.fromString(postMessage.getPostId());
            UUID authorId = UUID.fromString(postMessage.getAuthorUserId());

            // Удаляем пост из кеша автора
            feedCacheService.removePostFromUserFeed(authorId, postId);

            // Получаем друзей и удаляем пост из их лент
            List<UUID> friendIds = friendRepository.findFriendIds(authorId);
            if (!friendIds.isEmpty()) {
                for (UUID friendId : friendIds) {
                    feedCacheService.removePostFromUserFeed(friendId, postId);
                }
                log.info("Removed post {} from {} friends' feeds", postId, friendIds.size());
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process post deleted: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process post deleted", e);
        }
    }

    /**
     * Обработчик батчевого обновления лент
     */
    @KafkaListener(topics = "feed.update", groupId = "feed-updater", concurrency = "5")
    public void handleFeedUpdateTask(FeedUpdateTask task, Acknowledgment ack) {
        log.debug("Processing feed update batch {}/{} for post {}",
                task.getBatchIndex() + 1, task.getTotalBatches(), task.getPostId());

        try {
            Post post = postRepository.findById(task.getPostId());
            if (post == null) {
                log.warn("Post {} not found", task.getPostId());
                ack.acknowledge();
                return;
            }

            PostMessage postMessage = PostMessage.from(
                    ru.otus.study.dto.PostDto.builder()
                            .id(post.getId())
                            .text(post.getText())
                            .authorUserId(post.getAuthorUserId())
                            .createdAt(post.getCreatedAt())
                            .build()
            );

            int successCount = 0;

            for (UUID friendId : task.getFriendIds()) {
                try {
                    // Обновляем кеш ленты друга
                    feedCacheService.addPostToUserFeed(friendId, post);

                    // Отправляем WebSocket уведомление онлайн пользователям
                    if (notificationService.isUserConnected(friendId)) {
                        notificationService.notifyUserAboutNewPost(friendId, postMessage);
                    }

                    successCount++;

                } catch (Exception e) {
                    log.error("Failed to update feed for friend {}: {}", friendId, e.getMessage());
                }
            }

            log.debug("Completed batch {}/{}: {}/{} friends updated",
                    task.getBatchIndex() + 1, task.getTotalBatches(),
                    successCount, task.getFriendIds().size());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process feed update batch {}: {}",
                    task.getBatchIndex(), e.getMessage(), e);
            throw new RuntimeException("Failed to process feed update batch", e);
        }
    }

    /**
     * Обработчик материализации ленты
     */
    @KafkaListener(topics = "feed.materialize", groupId = "feed-materializer")
    public void handleFeedMaterialization(UUID userId, Acknowledgment ack) {
        log.info("Starting feed materialization for user {}", userId);
        long startTime = System.currentTimeMillis();

        try {
            // 1. Получаем друзей пользователя
            List<UUID> friendIds = friendRepository.findFriendIds(userId);

            if (friendIds.isEmpty()) {
                feedCacheService.clearUserFeed(userId);
                log.info("User {} has no friends, feed cleared", userId);
                ack.acknowledge();
                return;
            }

            // 2. Загружаем посты друзей из БД
            List<Post> allFriendPosts = postRepository.findRecentPostsByUserIds(friendIds, 1000);

            // 3. Перестраиваем ленту в Redis
            feedCacheService.rebuildUserFeed(userId, allFriendPosts);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Feed materialization for user {} completed: {} posts in {} ms",
                    userId, allFriendPosts.size(), duration);

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to materialize feed for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to materialize feed", e);
        }
    }
}