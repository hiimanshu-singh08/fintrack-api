package com.fintrack.api.service;

import com.fintrack.api.dto.RegisterRequest;
import com.fintrack.api.exception.UsernameAlreadyExistsException;
import com.fintrack.api.model.Role;
import com.fintrack.api.model.User;
import com.fintrack.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @Test
    void registerUser_savesHashedPassword_whenUsernameAvailable() {
        userService = new UserService(userRepository, passwordEncoder);
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("plaintext-password");

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("plaintext-password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = userService.registerUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed-password");
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
        assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(saved.getUsername()).isEqualTo("alice");
    }

    @Test
    void registerUser_throws_whenUsernameAlreadyExists() {
        userService = new UserService(userRepository, passwordEncoder);
        RegisterRequest request = new RegisterRequest();
        request.setUsername("bob");
        request.setPassword("plaintext-password");

        when(userRepository.existsByUsername("bob")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }
}
