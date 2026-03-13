package com.liuweiqing.aichat.controller;

import com.liuweiqing.aichat.dto.AuthResponse;
import com.liuweiqing.aichat.dto.LoginRequest;
import com.liuweiqing.aichat.dto.RegisterRequest;
import com.liuweiqing.aichat.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body("Username and password are required");
        }
        try {
            String token = userService.register(request.username(), request.password());
            log.info("User registered: {}", request.username());
            return ResponseEntity.ok(new AuthResponse(token, request.username()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            String token = userService.authenticate(request.username(), request.password());
            log.info("User logged in: {}", request.username());
            return ResponseEntity.ok(new AuthResponse(token, request.username()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String header) {
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            userService.logout(token);
            log.info("User logged out");
        }
        return ResponseEntity.ok().build();
    }
}
