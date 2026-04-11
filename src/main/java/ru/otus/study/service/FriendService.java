package ru.otus.study.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.study.model.Post;
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

        // Получаем последние посты нового друга
        List<UUID> newFriendOnly = List.of(friendId);
        List<Post> friendPosts = postRepository.findRecentPostsByUserIds(newFriendOnly, 100);

        // Добавляем посты нового друга в ленту пользователя
        if (!friendPosts.isEmpty()) {
            feedCacheService.warmUpFeedForNewFriend(userId, friendId, friendPosts);
        }
    }

    @Transactional
    public void removeFriend(UUID userId, UUID friendId) {
        friendRepository.removeFriend(userId, friendId);
        log.info("User {} removed friend {}", userId, friendId);

        // Полностью перестраиваем ленту пользователя без постов удаленного друга
        rebuildUserFeed(userId);
    }

    public List<UUID> getFriendIds(UUID userId) {
        return friendRepository.findFriendIds(userId);
    }

    private void rebuildUserFeed(UUID userId) {
        List<UUID> friendIds = friendRepository.findFriendIds(userId);

        if (friendIds.isEmpty()) {
            // Если друзей нет, очищаем ленту
            feedCacheService.rebuildUserFeed(userId, List.of());
        } else {
            // Иначе загружаем посты всех друзей
            List<Post> allFriendPosts = postRepository.findRecentPostsByUserIds(friendIds, 1000);
            feedCacheService.rebuildUserFeed(userId, allFriendPosts);
        }
    }
}