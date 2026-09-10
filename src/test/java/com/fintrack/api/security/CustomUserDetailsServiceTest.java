package com.fintrack.api.security;

import com.fintrack.api.model.Role;
import com.fintrack.api.model.User;
import com.fintrack.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void loadUserByUsername_returnsPrincipal_whenUserExists() {
        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);
        User user = new User();
        user.setId(5L);
        user.setUsername("dave");
        user.setPassword("hash");
        user.setRole(Role.USER);
        when(userRepository.findByUsername("dave")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("dave");

        assertThat(details.getUsername()).isEqualTo("dave");
        assertThat(((AppUserPrincipal) details).getId()).isEqualTo(5L);
    }

    @Test
    void loadUserByUsername_throws_whenUserMissing() {
        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
