package ru.otus.study.service;

import org.springframework.stereotype.Service;
import ru.otus.study.client.DialogRemoteClient;
import ru.otus.study.model.DialogMessage;

import java.util.List;
import java.util.UUID;

@Service
public class DialogService {

    private final DialogRemoteClient dialogRemoteClient;

    public DialogService(DialogRemoteClient dialogRemoteClient) {
        this.dialogRemoteClient = dialogRemoteClient;
    }

    public DialogMessage sendMessage(UUID fromUserId, UUID toUserId, String text) {
        return dialogRemoteClient.sendMessage(fromUserId, toUserId, text);
    }

    public List<DialogMessage> getDialogMessages(UUID currentUserId, UUID otherUserId) {
        return getDialogMessages(currentUserId, otherUserId, 50, 0);
    }

    public List<DialogMessage> getDialogMessages(UUID currentUserId, UUID otherUserId, int limit, int offset) {
        return dialogRemoteClient.getDialogMessages(currentUserId, otherUserId, limit, offset);
    }
}
