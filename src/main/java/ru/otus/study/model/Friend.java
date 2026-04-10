package ru.otus.study.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Friend {
    private UUID userId;
    private UUID friendId;
    private LocalDateTime createdAt;
}