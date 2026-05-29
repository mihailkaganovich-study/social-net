package ru.otus.study.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.otus.study.model.DialogMessage;
import ru.otus.study.service.DialogService;
import ru.otus.study.service.JwtService;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
@Slf4j
@RestController
@RequestMapping("/dialog")
public class DialogController {

    private final DialogService dialogService;
    private final JwtService jwtService;

    @Autowired
    public DialogController(DialogService dialogService, JwtService jwtService) {
        this.dialogService = dialogService;
        this.jwtService = jwtService;
    }

    @PostMapping("/{user_id}/send")
    public ResponseEntity<?> sendMessage(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("user_id") UUID toUserId,
            @RequestBody SendMessageRequest request) {

        try {
            String token = extractToken(authHeader);
            UUID currentUserId = jwtService.extractUserId(token);
            log.info("from_user={} to_user={}",currentUserId,toUserId);
            DialogMessage message = dialogService.sendMessage(
                    currentUserId,
                    toUserId,
                    request.getText()
            );

            return ResponseEntity.ok(new SendMessageResponse(message.getMessageId()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.info(Arrays.toString(e.getStackTrace()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to send message"));
        }
    }

    @GetMapping("/{user_id}/list")
    public ResponseEntity<?> getDialogMessages(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("user_id") UUID otherUserId,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {

        try {
            String token = extractToken(authHeader);
            UUID currentUserId = jwtService.extractUserId(token);
            if (offset < 0 || limit < 1) {
                return ResponseEntity.badRequest().build();
            }
            List<DialogMessage> messages = dialogService.getDialogMessages(
                    currentUserId,
                    otherUserId,
                    limit,
                    offset
            );

            // Отмечаем сообщения как прочитанные
            dialogService.markDialogAsRead(currentUserId, otherUserId);

            return ResponseEntity.ok(messages);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve messages"));
        }
    }
    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid authorization header");
    }
    // Вспомогательные классы
    static class SendMessageRequest {
        private String text;

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    static class SendMessageResponse {
        private final String messageId;
        private final String status = "sent";

        public SendMessageResponse(UUID messageId) {
            this.messageId = messageId.toString();
        }

        public String getMessageId() { return messageId; }
        public String getStatus() { return status; }
    }

    static class ErrorResponse {
        private final String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() { return error; }
    }
}