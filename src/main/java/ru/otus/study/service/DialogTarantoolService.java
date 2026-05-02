package ru.otus.study.service;

import org.springframework.stereotype.Service;
import ru.otus.study.model.DialogMessage;
import ru.otus.study.repository.tarantool.TarantoolConnectionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DialogTarantoolService {
    private static final String UDF_SAVE_MESSAGE = "dialog_save_message";
    private static final String UDF_GET_MESSAGES = "dialog_get_messages";
    private static final String UDF_MARK_AS_READ = "dialog_mark_as_read";

    private final TarantoolConnectionRepository connectionRepository;

    public DialogTarantoolService(TarantoolConnectionRepository connectionRepository) {
        this.connectionRepository = connectionRepository;
    }

    public DialogMessage saveMessage(UUID fromUserId, UUID toUserId, String text) {
        UUID dialogId = DialogMessage.generateDialogId(fromUserId, toUserId);
        UUID messageId = UUID.randomUUID();
        long createdAtMs = Instant.now().toEpochMilli();

        connectionRepository.callUdf(
                UDF_SAVE_MESSAGE,
                dialogId.toString(),
                messageId.toString(),
                fromUserId.toString(),
                toUserId.toString(),
                text,
                createdAtMs
        );

        DialogMessage message = new DialogMessage(fromUserId, toUserId, text);
        message.setDialogId(dialogId);
        message.setMessageId(messageId);
        message.setCreatedAt(Instant.ofEpochMilli(createdAtMs));
        message.setReadAt(null);
        return message;
    }

    public List<DialogMessage> getDialogMessages(UUID currentUserId, UUID otherUserId, int limit, int offset) {
        UUID dialogId = DialogMessage.generateDialogId(currentUserId, otherUserId);

        List<?> rawRows = connectionRepository.callUdf(
                UDF_GET_MESSAGES,
                dialogId.toString(),
                limit,
                offset
        );

        List<?> rows = normalizeRows(rawRows);
        List<DialogMessage> result = new ArrayList<>(rows.size());
        for (Object rawRow : rows) {
            DialogMessage parsed = parseRow(rawRow);
            result.add(parsed);
        }
        return result;
    }

    public void markDialogAsRead(UUID currentUserId, UUID otherUserId) {
        UUID dialogId = DialogMessage.generateDialogId(currentUserId, otherUserId);
        long readAtMs = Instant.now().toEpochMilli();

        connectionRepository.callUdf(
                UDF_MARK_AS_READ,
                dialogId.toString(),
                currentUserId.toString(),
                readAtMs
        );
    }

    @SuppressWarnings("unchecked")
    private List<?> normalizeRows(List<?> rawRows) {
        if (rawRows.isEmpty()) {
            return rawRows;
        }

        // Tarantool client may wrap Lua return into one top-level element: [ [row1, row2, ...] ]
        if (rawRows.size() == 1 && rawRows.get(0) instanceof List<?> nested) {
            if (nested.isEmpty()) {
                return nested;
            }
            Object firstNested = nested.get(0);
            if (firstNested instanceof List<?> || firstNested instanceof Object[]) {
                return nested;
            }
        }

        return rawRows;
    }

    private DialogMessage parseRow(Object rawRow) {
        // We expect UDF to return a tuple array:
        // [0]=dialog_id, [1]=message_id, [2]=from_user_id, [3]=to_user_id, [4]=text, [5]=created_at_ms, [6]=read_at_ms|nil
        if (rawRow instanceof List<?> row) {
            return parseRowFromList(row);
        }
        if (rawRow instanceof Object[] arr) {
            return parseRowFromArray(arr);
        }
        throw new IllegalStateException("Unexpected Tarantool UDF row type: " + rawRow.getClass());
    }

    private DialogMessage parseRowFromList(List<?> row) {
        if (row.size() < 7) {
            throw new IllegalStateException("Unexpected Tarantool UDF row size: " + row.size());
        }

        UUID dialogId = UUID.fromString(String.valueOf(row.get(0)));
        UUID messageId = UUID.fromString(String.valueOf(row.get(1)));
        UUID fromUserId = UUID.fromString(String.valueOf(row.get(2)));
        UUID toUserId = UUID.fromString(String.valueOf(row.get(3)));
        String text = String.valueOf(row.get(4));
        long createdAtMs = ((Number) row.get(5)).longValue();

        Object readAtObj = row.get(6);
        Instant readAt = null;
        if (readAtObj != null) {
            long readAtMs = ((Number) readAtObj).longValue();
            if (readAtMs > 0) {
                readAt = Instant.ofEpochMilli(readAtMs);
            }
        }

        DialogMessage message = new DialogMessage();
        message.setDialogId(dialogId);
        message.setMessageId(messageId);
        message.setFromUserId(fromUserId);
        message.setToUserId(toUserId);
        message.setText(text);
        message.setCreatedAt(Instant.ofEpochMilli(createdAtMs));
        message.setReadAt(readAt);
        return message;
    }

    private DialogMessage parseRowFromArray(Object[] arr) {
        if (arr.length < 7) {
            throw new IllegalStateException("Unexpected Tarantool UDF row length: " + arr.length);
        }

        UUID dialogId = UUID.fromString(String.valueOf(arr[0]));
        UUID messageId = UUID.fromString(String.valueOf(arr[1]));
        UUID fromUserId = UUID.fromString(String.valueOf(arr[2]));
        UUID toUserId = UUID.fromString(String.valueOf(arr[3]));
        String text = String.valueOf(arr[4]);
        long createdAtMs = ((Number) arr[5]).longValue();

        Object readAtObj = arr[6];
        Instant readAt = null;
        if (readAtObj != null) {
            long readAtMs = ((Number) readAtObj).longValue();
            if (readAtMs > 0) {
                readAt = Instant.ofEpochMilli(readAtMs);
            }
        }

        DialogMessage message = new DialogMessage();
        message.setDialogId(dialogId);
        message.setMessageId(messageId);
        message.setFromUserId(fromUserId);
        message.setToUserId(toUserId);
        message.setText(text);
        message.setCreatedAt(Instant.ofEpochMilli(createdAtMs));
        message.setReadAt(readAt);
        return message;
    }
}
