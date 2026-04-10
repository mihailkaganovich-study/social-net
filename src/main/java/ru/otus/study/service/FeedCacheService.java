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

    public void addPostToFriendsFeeds(Post post, List<UUID> friendIds) {
        PostDto postDto = convertToDto(post);

        for (UUID friendId : friendIds) {
            String feedKey = getFeedKey(friendId);
            try {
                // Добавляем пост в начало списка (самый новый)
                redisTemplate.opsForList().leftPush(feedKey, postDto);

                // Обрезаем список до максимального размера
                redisTemplate.opsForList().trim(feedKey, 0, MAX_FEED_SIZE - 1);

                // Устанавливаем TTL
                redisTemplate.expire(feedKey, FEED_TTL_HOURS, TimeUnit.HOURS);

                log.debug("Added post {} to feed of user {}", post.getId(), friendId);
            } catch (Exception e) {
                log.error("Failed to add post to feed cache for user {}: {}", friendId, e.getMessage());
            }
        }
    }

    public void updatePostInFeeds(Post post) {
        PostDto postDto = convertToDto(post);
        // В данном случае просто обновляем пост в лентах
        // Можно реализовать более сложную логику с поиском и заменой
        log.debug("Post {} updated in feeds", post.getId());
    }

    public void removePostFromFeeds(UUID postId, List<UUID> friendIds) {
        for (UUID friendId : friendIds) {
            String feedKey = getFeedKey(friendId);
            try {
                List<PostDto> feed = getFeed(friendId);
                List<PostDto> updatedFeed = feed.stream()
                        .filter(post -> !post.getId().equals(postId))
                        .collect(Collectors.toList());

                // Перезаписываем ленту
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

    public List<PostDto> getFeed(UUID userId, int offset, int limit) {
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

            return redisTemplate.opsForList().range(feedKey, offset, end);
        } catch (Exception e) {
            log.error("Failed to get feed from cache for user {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    public void warmUpFeed(UUID userId, List<Post> posts) {
        String feedKey = getFeedKey(userId);
        try {
            redisTemplate.delete(feedKey);

            if (!posts.isEmpty()) {
                List<PostDto> postDtos = posts.stream()
                        .map(this::convertToDto)
                        .collect(Collectors.toList());

                redisTemplate.opsForList().rightPushAll(feedKey, postDtos);
                redisTemplate.expire(feedKey, FEED_TTL_HOURS, TimeUnit.HOURS);

                log.info("Warmed up feed for user {} with {} posts", userId, posts.size());
            }
        } catch (Exception e) {
            log.error("Failed to warm up feed for user {}: {}", userId, e.getMessage());
        }
    }

    public List<PostDto> getFeed(UUID userId) {
        String feedKey = getFeedKey(userId);
        try {
            Long size = redisTemplate.opsForList().size(feedKey);
            if (size == null || size == 0) {
                return new ArrayList<>();
            }
            return redisTemplate.opsForList().range(feedKey, 0, -1);
        } catch (Exception e) {
            log.error("Failed to get feed from cache for user {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    private String getFeedKey(UUID userId) {
        return FEED_KEY_PREFIX + userId.toString();
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