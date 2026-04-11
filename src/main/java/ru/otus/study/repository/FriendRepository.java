package ru.otus.study.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class FriendRepository {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void addFriend(UUID userId, UUID friendId) {
        if (userId.equals(friendId)) {
            throw new IllegalArgumentException("Cannot add yourself as a friend");
        }

        // Упорядочиваем UUID для консистентности
        UUID firstId = userId.compareTo(friendId) < 0 ? userId : friendId;
        UUID secondId = userId.compareTo(friendId) < 0 ? friendId : userId;

        String sql = "INSERT INTO friends (user_id, friend_id) VALUES (?, ?) " +
                "ON CONFLICT (user_id, friend_id) DO NOTHING";

        jdbcTemplate.update(sql, firstId, secondId);
        log.debug("Added friendship between {} and {}", userId, friendId);
    }

    @Transactional
    public void removeFriend(UUID userId, UUID friendId) {
        UUID firstId = userId.compareTo(friendId) < 0 ? userId : friendId;
        UUID secondId = userId.compareTo(friendId) < 0 ? friendId : userId;

        String sql = "DELETE FROM friends WHERE user_id = ? AND friend_id = ?";
        int rowsAffected = jdbcTemplate.update(sql, firstId, secondId);
        log.debug("Removed friendship between {} and {}, rows affected: {}", userId, friendId, rowsAffected);
    }

    public List<UUID> findFriendIds(UUID userId) {
        String sql = """
            SELECT 
                CASE 
                    WHEN user_id = ? THEN friend_id 
                    ELSE user_id 
                END as friend_id
            FROM friends
            WHERE user_id = ? OR friend_id = ?
            ORDER BY created_at DESC
            """;

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> UUID.fromString(rs.getString("friend_id")),
                userId, userId, userId);
    }

    public boolean isFriend(UUID userId, UUID friendId) {
        UUID firstId = userId.compareTo(friendId) < 0 ? userId : friendId;
        UUID secondId = userId.compareTo(friendId) < 0 ? friendId : userId;

        String sql = "SELECT COUNT(*) FROM friends WHERE user_id = ? AND friend_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, firstId, secondId);
        return count != null && count > 0;
    }

    public Map<UUID, Boolean> areFriends(UUID userId, List<UUID> friendIds) {
        Map<UUID, Boolean> result = new HashMap<>();

        for (UUID friendId : friendIds) {
            result.put(friendId, isFriend(userId, friendId));
        }

        return result;
    }

    public List<UUID> findMutualFriends(UUID user1, UUID user2) {
        String sql = """
            SELECT 
                CASE 
                    WHEN f1.user_id = ? THEN f1.friend_id 
                    ELSE f1.user_id 
                END as mutual_friend_id
            FROM friends f1
            WHERE (f1.user_id = ? OR f1.friend_id = ?)
            INTERSECT
            SELECT 
                CASE 
                    WHEN f2.user_id = ? THEN f2.friend_id 
                    ELSE f2.user_id 
                END as mutual_friend_id
            FROM friends f2
            WHERE (f2.user_id = ? OR f2.friend_id = ?)
            """;

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> UUID.fromString(rs.getString("mutual_friend_id")),
                user1, user1, user1, user2, user2, user2);
    }
}