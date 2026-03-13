package com.liuweiqing.aichat.service;

import com.liuweiqing.aichat.model.User;
import com.liuweiqing.aichat.repository.UserRepository;
import com.liuweiqing.aichat.security.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 注册用户并返回 JWT token。
     */
    public String register(String username, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        User user = new User(username, passwordEncoder.encode(rawPassword));
        userRepository.save(user);
        return jwtUtil.generateToken(username);
    }

    /**
     * 验证用户并返回 JWT token。
     */
    public String authenticate(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        return jwtUtil.generateToken(username);
    }

    /**
     * 登出，从 Redis 中删除 token 使其失效。
     */
    public void logout(String token) {
        jwtUtil.removeToken(token);
    }
}
