package ru.otus.study.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.otus.study.model.DialogMessage;
import ru.otus.study.web.RequestIdConstants;
import ru.otus.study.web.RequestIdPropagation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class DialogRemoteClient {

    public static final String USER_ID_HEADER = "X-User-Id";

    private final RestTemplate restTemplate;

    public DialogRemoteClient(@Qualifier("dialogsRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public DialogMessage sendMessage(UUID fromUserId, UUID toUserId, String text) {
        try {
            HttpHeaders headers = userHeaders(fromUserId);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("text", text), headers);

            ResponseEntity<SendMessageResponse> response = restTemplate.exchange(
                    "/internal/dialog/{userId}/send",
                    HttpMethod.POST,
                    entity,
                    SendMessageResponse.class,
                    toUserId
            );

            SendMessageResponse body = response.getBody();
            if (body == null || body.getMessageId() == null) {
                throw new DialogRemoteException("Empty response from dialogs service");
            }

            DialogMessage message = new DialogMessage(fromUserId, toUserId, text);
            message.setMessageId(UUID.fromString(body.getMessageId()));
            return message;
        } catch (HttpStatusCodeException e) {
            throw mapException(e);
        }
    }

    public List<DialogMessage> getDialogMessages(UUID currentUserId, UUID otherUserId, int limit, int offset) {
        try {
            String uri = UriComponentsBuilder.fromPath("/internal/dialog/{userId}/list")
                    .queryParam("limit", limit)
                    .queryParam("offset", offset)
                    .buildAndExpand(otherUserId)
                    .toUriString();

            ResponseEntity<List<DialogMessage>> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    new HttpEntity<>(userHeaders(currentUserId)),
                    new ParameterizedTypeReference<List<DialogMessage>>() {
                    }
            );
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            throw mapException(e);
        }
    }

    private HttpHeaders userHeaders(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(USER_ID_HEADER, userId.toString());
        String requestId = RequestIdPropagation.current();
        if (requestId != null) {
            headers.set(RequestIdConstants.HEADER, requestId);
        }
        return headers;
    }

    private RuntimeException mapException(HttpStatusCodeException e) {
        if (e.getStatusCode().is4xxClientError()) {
            ErrorResponse body = e.getResponseBodyAs(ErrorResponse.class);
            String message = body != null && body.getError() != null
                    ? body.getError()
                    : e.getStatusText();
            return new IllegalArgumentException(message);
        }
        return new DialogRemoteException("Dialogs service error: " + e.getStatusCode(), e);
    }

    static class SendMessageResponse {
        private String messageId;
        private String status;

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    static class ErrorResponse {
        private String error;

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    public static class DialogRemoteException extends RuntimeException {
        public DialogRemoteException(String message) {
            super(message);
        }

        public DialogRemoteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
