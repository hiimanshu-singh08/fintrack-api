package com.fintrack.api.service;

import com.fintrack.api.dto.RegisterRequest;
import com.fintrack.api.exception.UsernameAlreadyExistsException;
import com.fintrack.api.model.Role;
import com.fintrack.api.model.User;
import com.fintrack.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(RegisterRequest request) {
        logger.info("Registering new user username={}", request.getUsername());
        if (userRepository.existsByUsername(request.getUsername())) {
            logger.warn("Registration rejected, username already exists: {}", request.getUsername());
            throw new UsernameAlreadyExistsException(request.getUsername());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
        return userRepository.save(user);
    }
}
