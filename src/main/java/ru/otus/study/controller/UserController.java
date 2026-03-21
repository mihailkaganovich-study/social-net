package ru.otus.study.controller;

import ru.otus.study.dto.*;
import ru.otus.study.model.User;
import ru.otus.study.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class UserController {
    
    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Optional<String> token = userService.login(loginRequest);
            if (token.isPresent()){
                return ResponseEntity.ok( new LoginResponse(token.get()));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body( new ErrorResponse(404, "User not found"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Login failed: " + e.getMessage()));
        }
    }

    @PostMapping("/user/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            UUID userId = userService.register(registerRequest);
            return ResponseEntity.ok(new UserIdResponse(userId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "Registration failed: " + e.getMessage()));
        }
    }

    @GetMapping("/user/get/{id}")
    public ResponseEntity<?> getUser(@PathVariable UUID id) {
        Optional<User> user = userService.getUserById(id);
        
        if (user.isPresent()) {
            return ResponseEntity.ok(user.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, "User not found"));
        }
    }
    // Поиск пользователей по имени
    @GetMapping("/user/search")
    public ResponseEntity<List<User>> search(
            @RequestParam String firstName, @RequestParam String secondName) {
        List<User> users = userService.search(firstName,secondName);
            return ResponseEntity.ok(users);
    }
}