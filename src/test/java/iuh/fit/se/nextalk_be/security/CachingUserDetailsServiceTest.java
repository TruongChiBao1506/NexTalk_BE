package iuh.fit.se.nextalk_be.security;

import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CachingUserDetailsServiceTest {

    private UserRepository userRepository;
    private CachingUserDetailsService cachingUserDetailsService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        cachingUserDetailsService = new CachingUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsername_Found_ReturnsUser() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setUsername("testuser");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        UserDetails result = cachingUserDetailsService.loadUserByUsername("user@example.com");

        assertNotNull(result);
        // User.getUsername() returns the username field (not the email).
        // findByEmail() call is the authoritative check that the right record was loaded.
        assertEquals("testuser", result.getUsername());
        verify(userRepository, times(1)).findByEmail("user@example.com");
    }

    @Test
    void loadUserByUsername_NotFound_ThrowsException() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () ->
                cachingUserDetailsService.loadUserByUsername("unknown@example.com"));
    }

    @Test
    void evict_doesNotThrow() {
        // evict() is a @CacheEvict stub — verify it can be called without error
        assertDoesNotThrow(() -> cachingUserDetailsService.evict("user@example.com"));
    }
}
