package ru.otus.study.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostMessage {
    private String postId;
    private String postText;
    private String authorUserId;

    public static PostMessage from(PostDto postDto) {
        return PostMessage.builder()
                .postId(postDto.getId().toString())
                .postText(postDto.getText())
                .authorUserId(postDto.getAuthorUserId().toString())
                .build();
    }
}