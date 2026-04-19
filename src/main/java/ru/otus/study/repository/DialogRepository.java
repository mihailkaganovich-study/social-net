package ru.otus.study.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.study.model.DialogMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class DialogRepository {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DialogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public DialogMessage saveMessage(DialogMessage message) {
        UUID dialogId = DialogMessage.generateDialogId(message.getFromUserId(), message.getToUserId());
        Instant now = Instant.now();
        Timestamp nowTs = Timestamp.from(now);

        // Вставляем сообщение
        String insertSql = """
            INSERT INTO dialog_messages (dialog_id, from_user_id, to_user_id, text, created_at) 
            VALUES (?, ?, ?, ?, ?)
            RETURNING message_id
            """;

        UUID messageId = jdbcTemplate.queryForObject(insertSql,
                (rs, rowNum) -> rs.getObject("message_id", UUID.class),
                dialogId,
                message.getFromUserId(),
                message.getToUserId(),
                message.getText(),
                nowTs
        );

        message.setDialogId(dialogId);
        message.setMessageId(messageId);
        message.setCreatedAt(now);

        // Обновляем информацию о диалоге
        updateDialogInfo(dialogId, message.getFromUserId(), message.getToUserId(), now);

        return message;
    }

    private void updateDialogInfo(UUID dialogId, UUID user1, UUID user2, Instant now) {
        Timestamp nowTs = Timestamp.from(now);

        // Проверяем существование диалога
        String checkSql = "SELECT 1 FROM dialogs WHERE dialog_id = ?";
        List<Integer> exists = jdbcTemplate.queryForList(checkSql, Integer.class, dialogId);

        if (exists.isEmpty()) {
            // Создаем новый диалог
            String insertSql = """
                INSERT INTO dialogs (dialog_id, user1_id, user2_id, created_at, last_message_at)
                VALUES (?, ?, ?, ?, ?)
                """;
            jdbcTemplate.update(insertSql, dialogId, user1, user2, nowTs, nowTs);
        } else {
            // Обновляем существующий
            String updateSql = """
                UPDATE dialogs 
                SET last_message_at = ? 
                WHERE dialog_id = ?
                """;
            jdbcTemplate.update(updateSql, nowTs, dialogId);
        }
    }

    public List<DialogMessage> getDialogMessages(UUID currentUserId, UUID otherUserId, int limit, int offset) {
        UUID dialogId = DialogMessage.generateDialogId(currentUserId, otherUserId);

        String sql = """
            SELECT dialog_id, message_id, from_user_id, to_user_id, text, created_at, read_at
            FROM dialog_messages
            WHERE dialog_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

        return jdbcTemplate.query(sql,
                new DialogMessageRowMapper(),
                dialogId, limit, offset
        );
    }

    public List<DialogMessage> getDialogMessages(UUID currentUserId, UUID otherUserId) {
        return getDialogMessages(currentUserId, otherUserId, 50, 0);
    }

    @Transactional
    public void markMessagesAsRead(UUID dialogId, UUID userId) {
        String sql = """
            UPDATE dialog_messages 
            SET read_at = ? 
            WHERE dialog_id = ? 
            AND to_user_id = ? 
            AND read_at IS NULL
            """;

        jdbcTemplate.update(sql, Timestamp.from(Instant.now()), dialogId, userId);
    }

    private static class DialogMessageRowMapper implements RowMapper<DialogMessage> {
        @Override
        public DialogMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
            DialogMessage message = new DialogMessage();
            message.setDialogId(rs.getObject("dialog_id", UUID.class));
            message.setMessageId(rs.getObject("message_id", UUID.class));
            message.setFromUserId(rs.getObject("from_user_id", UUID.class));
            message.setToUserId(rs.getObject("to_user_id", UUID.class));
            message.setText(rs.getString("text"));

            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                message.setCreatedAt(createdAt.toInstant());
            }

            Timestamp readAt = rs.getTimestamp("read_at");
            if (readAt != null) {
                message.setReadAt(readAt.toInstant());
            }

            return message;
        }
    }
}