package com.liuweiqing.aichat.controller;

import com.liuweiqing.aichat.dto.AuthResponse;
import com.liuweiqing.aichat.dto.LoginRequest;
import com.liuweiqing.aichat.dto.RegisterRequest;
import com.liuweiqing.aichat.security.JwtUtil;
import com.liuweiqing.aichat.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body("Username and password are required");
        }
        try {
            userService.register(request.username(), request.password());
            String token = jwtUtil.generateToken(request.username());
            log.info("User registered: {}", request.username());
            return ResponseEntity.ok(new AuthResponse(token, request.username()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            userService.authenticate(request.username(), request.password());
            String token = jwtUtil.generateToken(request.username());
            log.info("User logged in: {}", request.username());
            return ResponseEntity.ok(new AuthResponse(token, request.username()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }
}
