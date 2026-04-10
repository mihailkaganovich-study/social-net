package ru.otus.study.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.otus.study.dto.PostDto;
import ru.otus.study.service.JwtService;
import ru.otus.study.service.PostService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final JwtService jwtService;

    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createPost(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> request) {
        try {
            String token = extractToken(authHeader);
            UUID userId = jwtService.extractUserId(token);
            String text = request.get("text");

            if (text == null || text.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            PostDto post = postService.createPost(userId, text);
            return ResponseEntity.ok(Map.of("id", post.getId().toString()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/update")
    public ResponseEntity<Void> updatePost(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> request) {
        try {
            String token = extractToken(authHeader);
            UUID userId = jwtService.extractUserId(token);
            String postIdStr = request.get("id");
            String text = request.get("text");

            if (postIdStr == null || text == null || text.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            UUID postId = UUID.fromString(postIdStr);
            postService.updatePost(postId, userId, text);
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/delete/{id}")
    public ResponseEntity<Void> deletePost(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("id") String postIdStr) {
        try {
            String token = extractToken(authHeader);
            UUID userId = jwtService.extractUserId(token);
            UUID postId = UUID.fromString(postIdStr);

            postService.deletePost(postId, userId);
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<PostDto> getPost(@PathVariable("id") String postIdStr) {
        try {
            UUID postId = UUID.fromString(postIdStr);
            PostDto post = postService.getPost(postId);
            return ResponseEntity.ok(post);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/feed")
    public ResponseEntity<List<PostDto>> getFeed(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        try {
            String token = extractToken(authHeader);
            UUID userId = jwtService.extractUserId(token);

            if (offset < 0 || limit < 1) {
                return ResponseEntity.badRequest().build();
            }

            List<PostDto> feed = postService.getFeed(userId, offset, limit);
            return ResponseEntity.ok(feed);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid authorization header");
    }
}