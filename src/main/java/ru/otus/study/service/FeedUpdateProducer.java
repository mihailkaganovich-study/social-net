package ru.otus.study.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.otus.study.dto.FeedUpdateTask;
import ru.otus.study.dto.PostDto;
import ru.otus.study.dto.PostMessage;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedUpdateProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final FeedCacheService feedCacheService;

    private static final String POST_CREATED_TOPIC = "post.created";
    private static final String POST_UPDATED_TOPIC = "post.updated";
    private static final String POST_DELETED_TOPIC = "post.deleted";
    private static final String FEED_UPDATE_TOPIC = "feed.update";
    private static final String FEED_MATERIALIZE_TOPIC = "feed.materialize";

    /**
     * Публикует событие о создании нового поста
     */
    public void publishPostCreated(PostMessage postMessage) {
        kafkaTemplate.send(POST_CREATED_TOPIC, postMessage.getAuthorUserId(), postMessage)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish post created event: {}", ex.getMessage());
                    } else {
                        log.debug("Published post created event for post {}", postMessage.getPostId());
                    }
                });
    }

    /**
     * Публикует событие об обновлении поста
     */
    public void publishPostUpdated(PostMessage postMessage) {
        kafkaTemplate.send(POST_UPDATED_TOPIC, postMessage.getAuthorUserId(), postMessage)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish post updated event: {}", ex.getMessage());
                    } else {
                        log.debug("Published post updated event for post {}", postMessage.getPostId());
                    }
                });
    }

    /**
     * Публикует событие об удалении поста
     */
    public void publishPostDeleted(UUID postId, UUID authorId) {
        PostMessage message = PostMessage.builder()
                .postId(postId.toString())
                .authorUserId(authorId.toString())
                .build();

        kafkaTemplate.send(POST_DELETED_TOPIC, authorId.toString(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish post deleted event: {}", ex.getMessage());
                    } else {
                        log.debug("Published post deleted event for post {}", postId);
                    }
                });
    }

    /**
     * Создает задачи на обновление лент друзей с разбивкой на батчи
     */
    public void createFeedUpdateTasks(UUID postId, UUID authorId, List<UUID> friendIds, int batchSize) {
        if (friendIds.isEmpty()) {
            return;
        }

        int totalBatches = (int) Math.ceil((double) friendIds.size() / batchSize);
        for ( int i = 0; i < totalBatches; i++) {
            int start = i * batchSize;
            int end = Math.min(start + batchSize, friendIds.size());

            List<UUID> batchFriendIds = friendIds.subList(start, end);

            FeedUpdateTask task = FeedUpdateTask.builder()
                    .postId(postId)
                    .authorId(authorId)
                    .friendIds(batchFriendIds)
                    .batchIndex(i)
                    .totalBatches(totalBatches)
                    .timestamp(System.currentTimeMillis())
                    .build();

            int finalI = i;
            kafkaTemplate.send(FEED_UPDATE_TOPIC, postId.toString(), task)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish feed update task batch {}: {}", finalI, ex.getMessage());
                        } else {
                            log.debug("Published feed update task batch {}/{} for post {}",
                                    finalI + 1, totalBatches, postId);
                        }
                    });
        }

        log.info("Created {} feed update batches for post {} with {} friends",
                totalBatches, postId, friendIds.size());
    }

    /**
     * Планирует материализацию ленты пользователя
     */
    public void scheduleFeedMaterialization(UUID userId) {
        kafkaTemplate.send(FEED_MATERIALIZE_TOPIC, userId.toString(), userId)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to schedule feed materialization: {}", ex.getMessage());
                    } else {
                        log.debug("Scheduled feed materialization for user {}", userId);
                    }
                });
    }

    /**
     * Получает ленту из кеша
     */
    public List<PostDto> getFeedFromCache(UUID userId, int offset, int limit) {
        return feedCacheService.getFeed(userId, offset, limit);
    }
}