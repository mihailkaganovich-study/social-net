package ru.otus.study.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import ru.otus.study.dto.PostDto;
import ru.otus.study.model.Post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedCacheService {

    private final RedisTemplate<String, PostDto> redisTemplate;

    private static final String FEED_KEY_PREFIX = "feed:";
    private static final int MAX_FEED_SIZE = 1000;
    private static final long FEED_TTL_HOURS = 24;
    /**
     * Добавляет пост в ленту конкретного пользователя
     */
    public void addPostToUserFeed(UUID userId, Post post) {
        if (!isRedisAvailable()) {
            log.warn("Redis unavailable, skipping feed update for user {}", userId);
            return;
        }

        String feedKey = getFeedKey(userId);
        PostDto postDto = convertToDto(post);

        try {
            // Добавляем пост в начало ленты
            redisTemplate.opsForList().leftPush(feedKey, postDto);

            // Обрезаем до MAX_FEED_SIZE
            redisTemplate.opsForList().trim(feedKey, 0, MAX_FEED_SIZE - 1);

            // Обновляем TTL
            redisTemplate.expire(feedKey, FEED_TTL_HOURS, TimeUnit.HOURS);

            log.debug("Added post {} to feed of user {}", post.getId(), userId);
        } catch (Exception e) {
            log.error("Failed to add post to feed cache for user {}: {}", userId, e.getMessage());
        }
    }
    /**
     * Добавление поста в ленты всех друзей автора
     */
    public void addPostToFriendsFeeds(Post post, List<UUID> friendIds) {
        if (!isRedisAvailable()) {
            log.warn("Redis unavailable, skipping feed update for post {}", post.getId());
            return;
        }

        PostDto postDto = convertToDto(post);

        for (UUID friendId : friendIds) {
            String feedKey = getFeedKey(friendId);
            try {
                // Добавляем пост в начало ленты друга
                redisTemplate.opsForList().leftPush(feedKey, postDto);

                // Обрезаем ленту до MAX_FEED_SIZE
                redisTemplate.opsForList().trim(feedKey, 0, MAX_FEED_SIZE - 1);

                // Устанавливаем TTL
                redisTemplate.expire(feedKey, FEED_TTL_HOURS, TimeUnit.HOURS);

                log.debug("Added post {} to feed of user {}", post.getId(), friendId);
            } catch (Exception e) {
                log.error("Failed to add post to feed cache for user {}: {}", friendId, e.getMessage());
            }
        }
    }

    /**
     * Обновление поста в лентах друзей
     */
    public void updatePostInFriendsFeeds(Post post, List<UUID> friendIds) {
        if (!isRedisAvailable()) {
            log.warn("Redis unavailable, skipping feed update for post {}", post.getId());
            return;
        }

        PostDto postDto = convertToDto(post);

        for (UUID friendId : friendIds) {
            String feedKey = getFeedKey(friendId);
            try {
                List<PostDto> feed = getFeed(friendId);
                List<PostDto> updatedFeed = feed.stream()
                        .map(p -> p.getId().equals(post.getId()) ? postDto : p)
                        .collect(Collectors.toList());

                // Перезаписываем ленту
                redisTemplate.delete(feedKey);
                if (!updatedFeed.isEmpty()) {
                    redisTemplate.opsForList().rightPushAll(feedKey, updatedFeed);
                    redisTemplate.expire(feedKey, FEED_TTL_HOURS, TimeUnit.HOURS);
                }

                log.debug("Updated post {} in feed of user {}", post.getId(), friendId);
            } catch (Exception e) {
                log.error("Failed to update post in feed cache for user {}: {}", friendId, e.getMessage());
            }
        }
    }

    /**
     * Удаление поста из лент друзей
     */
    public void removePostFromFriendsFeeds(UUID postId, List<UUID> friendIds) {
        if (!isRedisAvailable()) {
            log.warn("Redis unavailable, skipping feed removal for post {}", postId);
            return;
        }

        for (UUID friendId : friendIds) {
            String feedKey = getFeedKey(friendId);
            try {
                List<PostDto> feed = getFeed(friendId);
                List<PostDto> updatedFeed = feed.stream()
                        .filter(post -> !post.getId().equals(postId))
                        .collect(Collectors.toList());

                redisTemplate.delete(feedKey);
                if (!updatedFeed.isEmpty()) {
                    redisTemplate.opsForList().rightPushAll(feedKey, updatedFeed);
                    redisTemplate.expire(feedKey, FEED_TTL_HOURS, TimeUnit.HOURS);
                }

                log.debug("Removed post {} from feed of user {}", postId, friendId);
            } catch (Exception e) {
                log.error("Failed to remove post from feed cache for user {}: {}", friendId, e.getMessage());
            }
        }
    }

    /**
     * Получение ленты пользователя
     */
    public List<PostDto> getFeed(UUID userId, int offset, int limit) {
        if (!isRedisAvailable()) {
            log.debug("Redis unavailable, returning empty feed for user {}", userId);
            return new ArrayList<>();
        }

        String feedKey = getFeedKey(userId);
        try {
            Long size = redisTemplate.opsForList().size(feedKey);
            if (size == null || size == 0) {
                return new ArrayList<>();
            }

            int end = Math.min(offset + limit - 1, size.intValue() - 1);
            if (offset > end) {
                return new ArrayList<>();
            }

            List<PostDto> posts = redisTemplate.opsForList().range(feedKey, offset, end);
            log.info("Get feed from redis for user {}",userId);
            return posts != null ? posts : new ArrayList<>();

        } catch (Exception e) {
            log.error("Failed to get feed from cache for user {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Прогрев ленты пользователя при добавлении нового друга
     */
    public void warmUpFeedForNewFriend(UUID userId, UUID newFriendId, List<Post> friendPosts) {
        if (!isRedisAvailable()) {
            log.debug("Redis unavailable, skipping feed warm-up for user {}", userId);
            return;
        }

        String feedKey = getFeedKey(userId);
        try {
            // Получаем текущую ленту
            List<PostDto> currentFeed = getFeed(userId);

            // Добавляем посты нового друга
            List<PostDto> newPosts = friendPosts.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

            // Объединяем и сортируем по дате (новые первыми)
            List<PostDto> updatedFeed = new ArrayList<>();
            updatedFeed.addAll(newPosts);
            updatedFeed.addAll(currentFeed);

            updatedFeed.sort((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()));

            // Оставляем только MAX_FEED_SIZE постов
            if (updatedFeed.size() > MAX_FEED_SIZE) {
                updatedFeed = updatedFeed.subList(0, MAX_FEED_SIZE);
            }

            // Сохраняем обновленную ленту
            redisTemplate.delete(feedKey);
            if (!updatedFeed.isEmpty()) {
                redisTemplate.opsForList().rightPushAll(feedKey, updatedFeed);
                redisTemplate.expire(feedKey, FEED_TTL_HOURS, TimeUnit.HOURS);
            }

            log.info("Updated feed for user {} with {} posts from new friend {}",
                    userId, newPosts.size(), newFriendId);

        } catch (Exception e) {
            log.error("Failed to warm up feed for user {}: {}", userId, e.getMessage());
        }
    }

// ru/otus/study/service/FeedCacheService.java (дополнительные методы)

    /**
     * Очищает ленту пользователя
     */
    public void clearUserFeed(UUID userId) {
        if (!isRedisAvailable()) {
            log.warn("Redis unavailable, skipping feed clear for user {}", userId);
            return;
        }

        String feedKey = getFeedKey(userId);
        try {
            redisTemplate.delete(feedKey);
            log.debug("Cleared feed for user {}", userId);
        } catch (Exception e) {
            log.error("Failed to clear feed for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Полная перестройка ленты пользователя
     */
    public void rebuildUserFeed(UUID userId, List<Post> posts) {
        if (!isRedisAvailable()) {
            log.debug("Redis unavailable, skipping feed rebuild for user {}", userId);
            return;
        }

        String feedKey = getFeedKey(userId);
        try {
            // Удаляем старую ленту
            redisTemplate.delete(feedKey);

            if (!posts.isEmpty()) {
                // Сортируем посты по дате (новые первыми)
                List<PostDto> postDtos = posts.stream()
                        .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()))
                        .map(this::convertToDto)
                        .collect(Collectors.toList());

                // Сохраняем в Redis
                redisTemplate.opsForList().rightPushAll(feedKey, postDtos);
                redisTemplate.expire(feedKey, FEED_TTL_HOURS, TimeUnit.HOURS);

                log.info("Rebuilt feed for user {} with {} posts", userId, postDtos.size());
            } else {
                log.info("Rebuilt empty feed for user {}", userId);
            }
        } catch (Exception e) {
            log.error("Failed to rebuild feed for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to rebuild user feed", e);
        }
    }
    private List<PostDto> getFeed(UUID userId) {
        String feedKey = getFeedKey(userId);
        try {
            Long size = redisTemplate.opsForList().size(feedKey);
            if (size == null || size == 0) {
                return new ArrayList<>();
            }

            List<PostDto> feed = redisTemplate.opsForList().range(feedKey, 0, -1);
            return feed != null ? feed : new ArrayList<>();

        } catch (Exception e) {
            log.error("Failed to get feed for user {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    private String getFeedKey(UUID userId) {
        return FEED_KEY_PREFIX + userId.toString();
    }

    private boolean isRedisAvailable() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            log.warn("Redis is not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Обновляет пост в ленте пользователя
     */
    public void updatePostInUserFeed(UUID userId, Post post) {
        if (!isRedisAvailable()) {
            return;
        }

        String feedKey = getFeedKey(userId);
        try {
            List<PostDto> feed = getFeed(userId);
            List<PostDto> updatedFeed = feed.stream()
                    .map(p -> p.getId().equals(post.getId()) ? convertToDto(post) : p)
                    .collect(Collectors.toList());

            redisTemplate.delete(feedKey);
            if (!updatedFeed.isEmpty()) {
                redisTemplate.opsForList().rightPushAll(feedKey, updatedFeed);
                redisTemplate.expire(feedKey, FEED_TTL_HOURS, TimeUnit.HOURS);
            }

            log.debug("Updated post {} in feed of user {}", post.getId(), userId);
        } catch (Exception e) {
            log.error("Failed to update post in feed: {}", e.getMessage());
        }
    }

    /**
     * Удаляет пост из ленты пользователя
     */
    public void removePostFromUserFeed(UUID userId, UUID postId) {
        if (!isRedisAvailable()) {
            return;
        }

        String feedKey = getFeedKey(userId);
        try {
            List<PostDto> feed = getFeed(userId);
            List<PostDto> updatedFeed = feed.stream()
                    .filter(p -> !p.getId().equals(postId))
                    .collect(Collectors.toList());

            redisTemplate.delete(feedKey);
            if (!updatedFeed.isEmpty()) {
                redisTemplate.opsForList().rightPushAll(feedKey, updatedFeed);
                redisTemplate.expire(feedKey, FEED_TTL_HOURS, TimeUnit.HOURS);
            }

            log.debug("Removed post {} from feed of user {}", postId, userId);
        } catch (Exception e) {
            log.error("Failed to remove post from feed: {}", e.getMessage());
        }
    }
    private PostDto convertToDto(Post post) {
        return PostDto.builder()
                .id(post.getId())
                .text(post.getText())
                .authorUserId(post.getAuthorUserId())
                .createdAt(post.getCreatedAt())
                .build();
    }
}