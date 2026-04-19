package ru.otus.study.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.study.model.DialogMessage;
import ru.otus.study.repository.DialogRepository;

import java.util.List;
import java.util.UUID;

@Service
public class DialogService {

    private final DialogRepository dialogRepository;

    @Autowired
    public DialogService(DialogRepository dialogRepository) {
        this.dialogRepository = dialogRepository;
    }

    @Transactional
    public DialogMessage sendMessage(UUID fromUserId, UUID toUserId, String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Message text cannot be empty");
        }

        if (text.length() > 10000) {
            throw new IllegalArgumentException("Message text too long");
        }

        DialogMessage message = new DialogMessage(fromUserId, toUserId, text);
        return dialogRepository.saveMessage(message);
    }

    public List<DialogMessage> getDialogMessages(UUID currentUserId, UUID otherUserId) {
        return dialogRepository.getDialogMessages(currentUserId, otherUserId);
    }

    public List<DialogMessage> getDialogMessages(UUID currentUserId, UUID otherUserId, int limit, int offset) {
        return dialogRepository.getDialogMessages(currentUserId, otherUserId, limit, offset);
    }

    @Transactional
    public void markDialogAsRead(UUID currentUserId, UUID otherUserId) {
        UUID dialogId = DialogMessage.generateDialogId(currentUserId, otherUserId);
        dialogRepository.markMessagesAsRead(dialogId, currentUserId);
    }
}