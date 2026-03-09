package ru.otus.study.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class LoginRequest {
    
    @NotNull(message = "User ID is required")
    @JsonProperty("id")
    private UUID id;
    
    @NotBlank(message = "Password is required")
    @JsonProperty("password")
    private String password;

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}