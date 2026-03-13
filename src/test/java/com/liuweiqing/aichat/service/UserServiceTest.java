package com.liuweiqing.aichat.service;

import com.liuweiqing.aichat.model.User;
import com.liuweiqing.aichat.repository.UserRepository;
import com.liuweiqing.aichat.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UserService userService;

    @Test
    void register_success() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenReturn(new User("testuser", "encoded_password"));
        when(jwtUtil.generateToken("testuser")).thenReturn("jwt_token");

        String token = userService.register("testuser", "password");

        assertEquals("jwt_token", token);
        verify(userRepository).save(any(User.class));
        verify(jwtUtil).generateToken("testuser");
    }

    @Test
    void register_duplicateUsername_throwsException() {
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> userService.register("existing", "password"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void authenticate_success() {
        User user = new User("testuser", "encoded_password");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encoded_password")).thenReturn(true);
        when(jwtUtil.generateToken("testuser")).thenReturn("jwt_token");

        String token = userService.authenticate("testuser", "password");

        assertEquals("jwt_token", token);
    }

    @Test
    void authenticate_wrongPassword_throwsException() {
        User user = new User("testuser", "encoded_password");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded_password")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> userService.authenticate("testuser", "wrong"));
    }

    @Test
    void authenticate_userNotFound_throwsException() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> userService.authenticate("unknown", "password"));
    }

    @Test
    void logout_callsRemoveToken() {
        userService.logout("some_token");

        verify(jwtUtil).removeToken("some_token");
    }
}
