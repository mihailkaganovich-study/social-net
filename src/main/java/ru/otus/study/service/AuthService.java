package ru.otus.study.service;

import ru.otus.study.dto.LoginRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    
    private final UserService userService;
    private final JwtService jwtService;

    @Autowired
    public AuthService(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    public Optional<String> authenticate(LoginRequest loginRequest) {
        UUID userId = loginRequest.getId();
        String password = loginRequest.getPassword();
        
        if (userService.validateUserPassword(userId, password)) {
            return Optional.of(jwtService.generateToken(userId));
        }
        
        return Optional.empty();
    }
}