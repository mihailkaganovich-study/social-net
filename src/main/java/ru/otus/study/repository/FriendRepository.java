package ru.otus.study.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import ru.otus.study.model.Friend;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class FriendRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Friend> friendRowMapper = (rs, rowNum) -> {
        Friend friend = new Friend();
        friend.setUserId(UUID.fromString(rs.getString("user_id")));
        friend.setFriendId(UUID.fromString(rs.getString("friend_id")));
        friend.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return friend;
    };

    public void addFriend(UUID userId, UUID friendId) {
        String sql = "INSERT INTO friends (user_id, friend_id) VALUES (?, ?) " +
                "ON CONFLICT (user_id, friend_id) DO NOTHING";
        jdbcTemplate.update(sql, userId, friendId);
    }

    public void removeFriend(UUID userId, UUID friendId) {
        String sql = "DELETE FROM friends WHERE user_id = ? AND friend_id = ?";
        jdbcTemplate.update(sql, userId, friendId);
    }

    public List<UUID> findFriendIds(UUID userId) {
        String sql = "SELECT friend_id FROM friends WHERE user_id = ?";
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> UUID.fromString(rs.getString("friend_id")),
                userId);
    }

    public boolean isFriend(UUID userId, UUID friendId) {
        String sql = "SELECT COUNT(*) FROM friends WHERE user_id = ? AND friend_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, friendId);
        return count != null && count > 0;
    }
}