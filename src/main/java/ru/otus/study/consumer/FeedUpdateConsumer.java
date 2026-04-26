// ru/otus/study/consumer/FeedUpdateConsumer.java
package ru.otus.study.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
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
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedUpdateConsumer {

    private final PostRepository postRepository;
    private final FriendRepository friendRepository;
    private final FeedCacheService feedCacheService;
    private final WebSocketNotificationService notificationService;
    private final FeedUpdateProducer feedUpdateProducer;
    private final StringRedisTemplate stringRedisTemplate;

    private static final int FRIENDS_BATCH_SIZE = 10;
    private static final String CELEBRITY_COUNTER_KEY = "celebrity:";
    private static final int CELEBRITY_THRESHOLD = 10;
    private static final int CELEBRITY_WINDOW_MINUTES = 5;

    /**
     * Обработчик создания поста - ОДИНОЧНЫЕ сообщения
     */
    @KafkaListener(
            topics = "post.created",
            groupId = "post-processor",
            containerFactory = "kafkaListenerContainerFactory"  // используем обычный (не batch) фабрику
    )
    public void handlePostCreated(@Payload PostMessage postMessage, Acknowledgment ack) {
        log.info("Processing post created: {}", postMessage.getPostId());
        long startTime = System.currentTimeMillis();

        try {
            UUID postId = UUID.fromString(postMessage.getPostId());
            UUID authorId = UUID.fromString(postMessage.getAuthorUserId());

            // 1. Получаем пост из БД
            Post post = postRepository.findById(postId);
            if (post == null) {
                log.warn("Post {} not found in database, skipping", postId);
                ack.acknowledge();
                return;
            }

            // 2. Добавляем пост в ленту автора
            feedCacheService.addPostToUserFeed(authorId, post);
            log.info("Added post {} to author's feed", postId);

            // 3. Проверяем на celebrity
            if (isCelebrity(authorId)) {
                log.warn("User {} detected as celebrity, using batch processing", authorId);
                processAsCelebrity(post, authorId);
            } else {
                // 4. Получаем друзей и обрабатываем
                List<UUID> friendIds = friendRepository.findFriendIds(authorId);
                if (!friendIds.isEmpty()) {
                    if (friendIds.size() > FRIENDS_BATCH_SIZE) {
                        // Много друзей - отправляем в батчи
                        log.info("Create task for proccess post for friends {}",friendIds);
                        feedUpdateProducer.createFeedUpdateTasks(postId, authorId, friendIds, FRIENDS_BATCH_SIZE);
                    } else {
                        // Мало друзей - обрабатываем синхронно
                        log.info("Proccess post for friends {}",friendIds);
                        processFriendFeeds(post, friendIds);
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Post {} processed in {} ms", postId, duration);

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process post {}: {}", postMessage.getPostId(), e.getMessage(), e);
            // Не подтверждаем, чтобы сообщение было обработано повторно
            throw new RuntimeException("Failed to process post created", e);
        }
    }

    /**
     * Обработчик обновления поста - ОДИНОЧНЫЕ сообщения
     */
    @KafkaListener(
            topics = "post.updated",
            groupId = "post-processor",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePostUpdated(@Payload PostMessage postMessage, Acknowledgment ack) {
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

            feedCacheService.updatePostInUserFeed(authorId, post);

            List<UUID> friendIds = friendRepository.findFriendIds(authorId);
            if (!friendIds.isEmpty()) {
                if (isCelebrity(authorId) || friendIds.size() > FRIENDS_BATCH_SIZE) {
                    feedUpdateProducer.createFeedUpdateTasks(postId, authorId, friendIds, FRIENDS_BATCH_SIZE);
                } else {
                    processFriendFeedsUpdate(post, friendIds);
                }
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process post update: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process post updated", e);
        }
    }

    /**
     * Обработчик удаления поста - ОДИНОЧНЫЕ сообщения
     */
    @KafkaListener(
            topics = "post.deleted",
            groupId = "post-processor",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePostDeleted(@Payload PostMessage postMessage, Acknowledgment ack) {
        log.info("Processing post deleted: {}", postMessage.getPostId());

        try {
            UUID postId = UUID.fromString(postMessage.getPostId());
            UUID authorId = UUID.fromString(postMessage.getAuthorUserId());

            feedCacheService.removePostFromUserFeed(authorId, postId);

            List<UUID> friendIds = friendRepository.findFriendIds(authorId);
            for (UUID friendId : friendIds) {
                feedCacheService.removePostFromUserFeed(friendId, postId);
            }

            log.info("Removed post {} from {} feeds", postId, friendIds.size());
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process post deleted: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process post deleted", e);
        }
    }

    /**
     * Обработчик батчевого обновления лент - BATCH сообщения
     */
    @KafkaListener(
            topics = "feed.update",
            groupId = "feed-updater",
            containerFactory = "batchFactory"  // используем batch фабрику
    )
    public void handleFeedUpdateTask(@Payload List<FeedUpdateTask> tasks, Acknowledgment ack) {
        log.info("Processing {} feed update tasks", tasks.size());

        int totalSuccess = 0;
        int totalFailure = 0;

        for (FeedUpdateTask task : tasks) {
            try {
                Post post = postRepository.findById(task.getPostId());
                if (post == null) {
                    log.warn("Post {} not found, skipping batch {}/{}",
                            task.getPostId(), task.getBatchIndex() + 1, task.getTotalBatches());
                    totalFailure += task.getFriendIds().size();
                    continue;
                }

                // Обрабатываем батч друзей
                int[] results = processFriendFeeds(post, task.getFriendIds());
                totalSuccess += results[0];
                totalFailure += results[1];

                log.debug("Batch {}/{} for post {}: {} success, {} failure",
                        task.getBatchIndex() + 1, task.getTotalBatches(),
                        task.getPostId(), results[0], results[1]);

            } catch (Exception e) {
                log.error("Failed to process batch {} for post {}: {}",
                        task.getBatchIndex(), task.getPostId(), e.getMessage());
                totalFailure += task.getFriendIds().size();
            }
        }

        log.info("Processed batch: {} total success, {} total failure", totalSuccess, totalFailure);
        ack.acknowledge();
    }

    /**
     * Обработчик материализации ленты
     */
    @KafkaListener(
            topics = "feed.materialize",
            groupId = "feed-materializer",
            containerFactory = "materializeFactory"
    )
    public void handleFeedMaterialization(@Payload UUID userId, Acknowledgment ack) {
        log.info("Starting feed materialization for user {}", userId);
        long startTime = System.currentTimeMillis();

        try {
            List<UUID> friendIds = friendRepository.findFriendIds(userId);

            if (friendIds.isEmpty()) {
                feedCacheService.clearUserFeed(userId);
                log.info("User {} has no friends, feed cleared", userId);
                ack.acknowledge();
                return;
            }

            List<Post> allFriendPosts = postRepository.findRecentPostsByUserIds(friendIds, 1000);
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

    // ==== Вспомогательные методы ====

    private int[] processFriendFeeds(Post post, List<UUID> friendIds) {
        PostMessage postMessage = PostMessage.from(
                ru.otus.study.dto.PostDto.builder()
                        .id(post.getId())
                        .text(post.getText())
                        .authorUserId(post.getAuthorUserId())
                        .createdAt(post.getCreatedAt())
                        .build()
        );

        int success = 0;
        int failure = 0;

        for (UUID friendId : friendIds) {
            try {
                feedCacheService.addPostToUserFeed(friendId, post);
                success++;

                notificationService.notifyUserAboutNewPost(friendId, postMessage);
            } catch (Exception e) {
                failure++;
                log.error("Failed to update feed for friend {}: {}", friendId, e.getMessage());
            }
        }

        return new int[]{success, failure};
    }

    private void processFriendFeedsUpdate(Post post, List<UUID> friendIds) {
        for (UUID friendId : friendIds) {
            try {
                feedCacheService.updatePostInUserFeed(friendId, post);
            } catch (Exception e) {
                log.error("Failed to update feed for friend {}: {}", friendId, e.getMessage());
            }
        }
    }

    private void processAsCelebrity(Post post, UUID authorId) {
        List<UUID> friendIds = friendRepository.findFriendIds(authorId);
        if (!friendIds.isEmpty()) {
            feedUpdateProducer.createFeedUpdateTasks(post.getId(), authorId, friendIds, FRIENDS_BATCH_SIZE);
            // Планируем материализацию для друзей
            for (UUID friendId : friendIds) {
                feedUpdateProducer.scheduleFeedMaterialization(friendId);
            }
            log.info("Celebrity {}: scheduled materialization for {} friends", authorId, friendIds.size());
        }
    }

    private boolean isCelebrity(UUID userId) {
        try {
            String counterKey = CELEBRITY_COUNTER_KEY + userId.toString();
            Long postCount = stringRedisTemplate.opsForValue().increment(counterKey);

            if (postCount != null && postCount == 1) {
                stringRedisTemplate.expire(counterKey, CELEBRITY_WINDOW_MINUTES, TimeUnit.MINUTES);
            }

            boolean isCelebrity = postCount != null && postCount > CELEBRITY_THRESHOLD;
            if (isCelebrity) {
                log.warn("User {} flagged as celebrity: {} posts in {} minutes",
                        userId, postCount, CELEBRITY_WINDOW_MINUTES);
            }

            return isCelebrity;
        } catch (Exception e) {
            log.error("Failed to check celebrity status: {}", e.getMessage());
            return false;
        }
    }
}