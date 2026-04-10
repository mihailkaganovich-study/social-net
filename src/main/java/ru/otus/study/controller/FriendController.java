package ru.otus.study.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.otus.study.service.FriendService;
import ru.otus.study.service.JwtService;

import java.util.UUID;

@RestController
@RequestMapping("/api/friend")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;
    private final JwtService jwtService;

    @PutMapping("/set/{user_id}")
    public ResponseEntity<Void> addFriend(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("user_id") String friendIdStr) {
        try {
            String token = extractToken(authHeader);
            UUID userId = jwtService.extractUserId(token);
            UUID friendId = UUID.fromString(friendIdStr);

            friendService.addFriend(userId, friendId);
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/delete/{user_id}")
    public ResponseEntity<Void> removeFriend(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("user_id") String friendIdStr) {
        try {
            String token = extractToken(authHeader);
            UUID userId = jwtService.extractUserId(token);
            UUID friendId = UUID.fromString(friendIdStr);

            friendService.removeFriend(userId, friendId);
            return ResponseEntity.ok().build();

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