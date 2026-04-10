package ru.otus.study.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.study.repository.FriendRepository;
import ru.otus.study.repository.PostRepository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRepository friendRepository;
    private final PostRepository postRepository;
    private final FeedCacheService feedCacheService;

    @Transactional
    public void addFriend(UUID userId, UUID friendId) {
        if (userId.equals(friendId)) {
            throw new IllegalArgumentException("Cannot add yourself as a friend");
        }

        friendRepository.addFriend(userId, friendId);
        log.info("User {} added friend {}", userId, friendId);

        // При добавлении друга, обновляем ленту пользователя
        updateUserFeedWithFriendPosts(userId, friendId);
    }

    @Transactional
    public void removeFriend(UUID userId, UUID friendId) {
        friendRepository.removeFriend(userId, friendId);
        log.info("User {} removed friend {}", userId, friendId);

        // При удалении друга, перестраиваем ленту пользователя
        rebuildUserFeed(userId);
    }

    public List<UUID> getFriendIds(UUID userId) {
        return friendRepository.findFriendIds(userId);
    }

    private void updateUserFeedWithFriendPosts(UUID userId, UUID friendId) {
        // Получаем последние посты нового друга и добавляем их в ленту
        List<UUID> friendIds = List.of(friendId);
        var recentPosts = postRepository.findRecentPostsByUserIds(friendIds, 100);

        // Прогреваем кеш для пользователя с постами нового друга
        List<UUID> allFriendIds = friendRepository.findFriendIds(userId);
        var allFriendPosts = postRepository.findRecentPostsByUserIds(allFriendIds, 1000);
        feedCacheService.warmUpFeed(userId, allFriendPosts);
    }

    private void rebuildUserFeed(UUID userId) {
        List<UUID> friendIds = friendRepository.findFriendIds(userId);
        var allFriendPosts = postRepository.findRecentPostsByUserIds(friendIds, 1000);
        feedCacheService.warmUpFeed(userId, allFriendPosts);
    }
}