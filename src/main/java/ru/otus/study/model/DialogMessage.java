package ru.otus.study.model;

import java.time.Instant;
import java.util.UUID;

public class DialogMessage {
    private UUID dialogId;
    private UUID messageId;
    private UUID fromUserId;
    private UUID toUserId;
    private String text;
    private Instant createdAt;
    private Instant readAt;

    // Конструкторы
    public DialogMessage() {}

    public DialogMessage(UUID fromUserId, UUID toUserId, String text) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.text = text;
    }

    // Геттеры и сеттеры
    public UUID getDialogId() { return dialogId; }
    public void setDialogId(UUID dialogId) { this.dialogId = dialogId; }

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }

    public UUID getFromUserId() { return fromUserId; }
    public void setFromUserId(UUID fromUserId) { this.fromUserId = fromUserId; }

    public UUID getToUserId() { return toUserId; }
    public void setToUserId(UUID toUserId) { this.toUserId = toUserId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }

    // Утилитарный метод для генерации детерминированного dialog_id
    public static UUID generateDialogId(UUID user1, UUID user2) {
        UUID min = user1.compareTo(user2) < 0 ? user1 : user2;
        UUID max = user1.compareTo(user2) < 0 ? user2 : user1;
        return UUID.nameUUIDFromBytes((min.toString() + "-" + max.toString()).getBytes());
    }
}