package iuh.fit.se.nextalk_be.security;

import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.RefreshTokenRepository;
import iuh.fit.se.nextalk_be.service.SessionRevocationService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterSecurityTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lockedAccountCannotAuthenticateWithExistingAccessToken() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UserDetailsService users = mock(UserDetailsService.class);
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        SessionRevocationService revocations = mock(SessionRevocationService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, users, tokens, revocations);
        User lockedUser = User.builder()
                .email("locked@example.test")
                .username("locked")
                .isAccountLocked(true)
                .build();
        lockedUser.setId("user-1");

        when(jwtService.extractUsername("access-token")).thenReturn(lockedUser.getEmail());
        when(users.loadUserByUsername(lockedUser.getEmail())).thenReturn(lockedUser);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(revocations).revokeAllForUser(lockedUser);
        verify(chain).doFilter(request, response);
    }
}
