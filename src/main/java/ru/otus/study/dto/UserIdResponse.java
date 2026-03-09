package ru.otus.study.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public class UserIdResponse {
    
    @JsonProperty("user_id")
    private UUID userId;

    public UserIdResponse(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
}