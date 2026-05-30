package com.pravoos.user.service;

import com.pravoos.user.exception.InvalidCredentialsException;
import com.pravoos.user.model.dto.LoginRequest;
import com.pravoos.user.model.dto.LoginResponse;
import com.pravoos.user.model.entity.User;
import com.pravoos.user.model.enums.UserStatus;
import com.pravoos.user.repository.UserRepository;
import com.pravoos.user.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOa8KxQ8q1xWqU9oN3i6Yk3v1lQ0Q3pK";

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndStatus(request.email(), UserStatus.ACTIVE)
                .orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            log.warn("Failed login attempt for unknown/inactive email: {}", request.email());
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Failed login attempt for email: {}", request.email());
            throw new InvalidCredentialsException();
        }

        log.info("User authenticated: {}", request.email());
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new LoginResponse(token, user.getId(), user.getEmail(), user.getRole());
    }
}
