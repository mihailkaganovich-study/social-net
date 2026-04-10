package ru.otus.study.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.study.dto.PostDto;
import ru.otus.study.model.Post;
import ru.otus.study.repository.FriendRepository;
import ru.otus.study.repository.PostRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final FriendRepository friendRepository;
    private final FeedCacheService feedCacheService;

    @Transactional
    public PostDto createPost(UUID authorUserId, String text) {
        Post post = postRepository.create(authorUserId, text);
        log.info("User {} created post {}", authorUserId, post.getId());

        // Получаем список друзей автора
        List<UUID> friendIds = friendRepository.findFriendIds(authorUserId);

        // Добавляем пост в ленты друзей
        if (!friendIds.isEmpty()) {
            feedCacheService.addPostToFriendsFeeds(post, friendIds);
        }

        return convertToDto(post);
    }

    @Transactional
    public PostDto updatePost(UUID postId, UUID authorUserId, String text) {
        Post post = postRepository.update(postId, authorUserId, text);
        if (post == null) {
            throw new IllegalArgumentException("Post not found or you are not the author");
        }

        log.info("User {} updated post {}", authorUserId, postId);

        // Обновляем пост в лентах друзей
        List<UUID> friendIds = friendRepository.findFriendIds(authorUserId);
        if (!friendIds.isEmpty()) {
            feedCacheService.updatePostInFeeds(post);
        }

        return convertToDto(post);
    }

    @Transactional
    public void deletePost(UUID postId, UUID authorUserId) {
        // Получаем пост перед удалением для получения списка друзей
        Post post = postRepository.findById(postId);
        if (post == null || !post.getAuthorUserId().equals(authorUserId)) {
            throw new IllegalArgumentException("Post not found or you are not the author");
        }

        boolean deleted = postRepository.delete(postId, authorUserId);
        if (!deleted) {
            throw new IllegalArgumentException("Failed to delete post");
        }

        log.info("User {} deleted post {}", authorUserId, postId);

        // Удаляем пост из лент друзей
        List<UUID> friendIds = friendRepository.findFriendIds(authorUserId);
        if (!friendIds.isEmpty()) {
            feedCacheService.removePostFromFeeds(postId, friendIds);
        }
    }

    public PostDto getPost(UUID postId) {
        Post post = postRepository.findById(postId);
        if (post == null) {
            throw new IllegalArgumentException("Post not found");
        }
        return convertToDto(post);
    }

    public List<PostDto> getFeed(UUID userId, int offset, int limit) {
        // Пробуем получить ленту из кеша
        List<PostDto> cachedFeed = feedCacheService.getFeed(userId, offset, limit);

        if (!cachedFeed.isEmpty() || offset > 0) {
            return cachedFeed;
        }

        // Если кеш пустой, загружаем из БД и прогреваем кеш
        List<UUID> friendIds = friendRepository.findFriendIds(userId);
        if (friendIds.isEmpty()) {
            return List.of();
        }

        List<Post> posts = postRepository.findRecentPostsByUserIds(friendIds, 1000);
        feedCacheService.warmUpFeed(userId, posts);

        // Возвращаем запрошенную страницу
        return posts.stream()
                .skip(offset)
                .limit(limit)
                .map(this::convertToDto)
                .collect(Collectors.toList());
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