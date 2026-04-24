package ru.otus.study.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.study.dto.PostDto;
import ru.otus.study.dto.PostMessage;
import ru.otus.study.model.Post;
import ru.otus.study.repository.PostRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final FeedUpdateProducer feedUpdateProducer;

    @Transactional
    public PostDto createPost(UUID authorUserId, String text) {
        // 1. Сохраняем пост в БД
        Post post = postRepository.create(authorUserId, text);
        log.info("User {} created post {}", authorUserId, post.getId());

        // 2. Отправляем событие в Kafka и сразу возвращаем ответ
        PostMessage postMessage = PostMessage.from(convertToDto(post));
        feedUpdateProducer.publishPostCreated(postMessage);

        log.debug("Published post created event for post {}", post.getId());

        // 3. Сразу возвращаем ответ клиенту
        return convertToDto(post);
    }

    @Transactional
    public PostDto updatePost(UUID postId, UUID authorUserId, String text) {
        Post post = postRepository.update(postId, authorUserId, text);
        if (post == null) {
            throw new IllegalArgumentException("Post not found or you are not the author");
        }

        log.info("User {} updated post {}", authorUserId, postId);

        // Отправляем событие обновления в Kafka
        PostMessage postMessage = PostMessage.from(convertToDto(post));
        feedUpdateProducer.publishPostUpdated(postMessage);

        return convertToDto(post);
    }

    @Transactional
    public void deletePost(UUID postId, UUID authorUserId) {
        Post post = postRepository.findById(postId);
        if (post == null || !post.getAuthorUserId().equals(authorUserId)) {
            throw new IllegalArgumentException("Post not found or you are not the author");
        }

        boolean deleted = postRepository.delete(postId, authorUserId);
        if (!deleted) {
            throw new IllegalArgumentException("Failed to delete post");
        }

        log.info("User {} deleted post {}", authorUserId, postId);

        // Отправляем событие удаления в Kafka
        feedUpdateProducer.publishPostDeleted(postId, authorUserId);
    }

    public PostDto getPost(UUID postId) {
        Post post = postRepository.findById(postId);
        if (post == null) {
            throw new IllegalArgumentException("Post not found");
        }
        return convertToDto(post);
    }

    public List<PostDto> getFeed(UUID userId, int offset, int limit) {
        // Лента всегда берется из кеша, который обновляется асинхронно через Kafka
        return feedUpdateProducer.getFeedFromCache(userId, offset, limit);
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