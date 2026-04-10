package ru.otus.study.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Post {
    private UUID id;
    private UUID authorUserId;
    private String text;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}