package ru.otus.study.service;

import ru.otus.study.dto.LoginRequest;
import ru.otus.study.dto.RegisterRequest;
import ru.otus.study.model.User;
import ru.otus.study.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {
    
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Autowired
    public UserService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public UUID register(RegisterRequest request) {
        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setSecondName(request.getSecondName());
        user.setBirthdate(request.getBirthdate());
        user.setBiography(request.getBiography());
        user.setCity(request.getCity());
        user.setPassword(request.getPassword());
        
        return userRepository.save(user);
    }
    public Optional<String> login(LoginRequest loginRequest){
        UUID userId = loginRequest.getId();
        String password = loginRequest.getPassword();

        if (validateUserPassword(userId, password)) {
            return Optional.of(jwtService.generateToken(userId));
        }

        return Optional.empty();

    }
    public Optional<User> getUserById(UUID id) {
        return userRepository.findById(id);
    }

    public boolean validateUserPassword(UUID userId, String password) {
        if (!userRepository.existsById(userId)) {
            return false;
        }
        return userRepository.validatePassword(userId, password);
    }

    public List<User> search(String firstName, String secondName){
        return userRepository.search(firstName,secondName);
    }
}