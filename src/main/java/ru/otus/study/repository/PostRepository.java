package ru.otus.study.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.study.model.Post;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Post> postRowMapper = (rs, rowNum) -> {
        Post post = new Post();
        post.setId(UUID.fromString(rs.getString("id")));
        post.setAuthorUserId(UUID.fromString(rs.getString("author_user_id")));
        post.setText(rs.getString("text"));
        post.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        post.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return post;
    };

    @Transactional
    public Post create(UUID authorUserId, String text) {
        UUID postId = UUID.randomUUID();
        String sql = "INSERT INTO posts (id, author_user_id, text) VALUES (?, ?, ?) RETURNING *";

        return jdbcTemplate.queryForObject(sql, postRowMapper, postId, authorUserId, text);
    }

    @Transactional
    public Post update(UUID postId, UUID authorUserId, String text) {
        String sql = "UPDATE posts SET text = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id = ? AND author_user_id = ? RETURNING *";

        List<Post> posts = jdbcTemplate.query(sql, postRowMapper, text, postId, authorUserId);
        return posts.isEmpty() ? null : posts.get(0);
    }

    @Transactional
    public boolean delete(UUID postId, UUID authorUserId) {
        String sql = "DELETE FROM posts WHERE id = ? AND author_user_id = ?";
        int rowsAffected = jdbcTemplate.update(sql, postId, authorUserId);
        return rowsAffected > 0;
    }

    public Post findById(UUID postId) {
        String sql = "SELECT * FROM posts WHERE id = ?";
        List<Post> posts = jdbcTemplate.query(sql, postRowMapper, postId);
        return posts.isEmpty() ? null : posts.get(0);
    }

    public List<Post> findPostsByUserIds(List<UUID> userIds, int offset, int limit) {
        if (userIds.isEmpty()) {
            return List.of();
        }

        String sql = "SELECT * FROM posts WHERE author_user_id IN (" +
                String.join(",", userIds.stream().map(id -> "?").toArray(String[]::new)) +
                ") ORDER BY created_at DESC LIMIT ? OFFSET ?";

        Object[] params = new Object[userIds.size() + 2];
        for (int i = 0; i < userIds.size(); i++) {
            params[i] = userIds.get(i);
        }
        params[userIds.size()] = limit;
        params[userIds.size() + 1] = offset;

        return jdbcTemplate.query(sql, postRowMapper, params);
    }

    public List<Post> findRecentPostsByUserIds(List<UUID> userIds, int limit) {
        return findPostsByUserIds(userIds, 0, limit);
    }
}