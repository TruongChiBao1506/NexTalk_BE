package iuh.fit.se.nextalk_be.auth;

import iuh.fit.se.nextalk_be.dto.request.LoginRequest;
import iuh.fit.se.nextalk_be.dto.request.RegisterRequest;
import iuh.fit.se.nextalk_be.dto.response.LoginResponse;
import iuh.fit.se.nextalk_be.dto.response.RegisterResponse;
import iuh.fit.se.nextalk_be.dto.response.UserProfileResponse;
import iuh.fit.se.nextalk_be.entity.EmailVerification;
import iuh.fit.se.nextalk_be.entity.RefreshToken;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.EmailVerificationRepository;
import iuh.fit.se.nextalk_be.repository.RefreshTokenRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.security.JwtService;
import iuh.fit.se.nextalk_be.service.AuthService;
import iuh.fit.se.nextalk_be.service.MailService;
import iuh.fit.se.nextalk_be.service.UserService;


import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private MailService mailService;

    @Mock
    private UserService userService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private User user;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .email("test@gmail.com")
                .username("testuser")
                .password("password123")
                .build();

        user = User.builder()
                .email("test@gmail.com")
                .username("testuser")
                .password("encodedPassword")
                .status("OFFLINE")
                .isVerified(false)
                .build();
        user.setId(UUID.randomUUID().toString());
    }

    @Test
    void register_Success() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(registerRequest.getUsername())).thenReturn(false);
        when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        RegisterResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertEquals(user.getEmail(), response.getEmail());
        assertFalse(response.isVerified());
        verify(emailVerificationRepository, times(1)).save(any(EmailVerification.class));
        verify(mailService, times(1)).sendVerificationEmail(eq(user.getEmail()), anyString());
    }

    @Test
    void register_DuplicateEmail_ThrowsBadRequestException() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.register(registerRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void verifyEmail_Success() {
        EmailVerification verification = EmailVerification.builder()
                .user(user)
                .token("valid-token")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .verified(false)
                .build();

        when(emailVerificationRepository.findByToken("valid-token")).thenReturn(Optional.of(verification));

        authService.verifyEmail("valid-token");

        assertTrue(user.isVerified());
        assertTrue(verification.isVerified());
        verify(userRepository, times(1)).save(user);
        verify(emailVerificationRepository, times(1)).save(verification);
    }

    @Test
    void verifyEmail_TokenNotFound_ThrowsResourceNotFoundException() {
        when(emailVerificationRepository.findByToken("invalid-token")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.verifyEmail("invalid-token"));
    }

    @Test
    void login_Success() {
        user.setVerified(true);
        LoginRequest loginRequest = new LoginRequest("test@gmail.com", "password123");

        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("accessToken");
        when(jwtService.generateRefreshToken(user)).thenReturn("refreshToken");
        when(jwtService.getRefreshExpirationMs()).thenReturn(604800000L);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit Browser");
        when(userService.mapToProfileResponse(user)).thenReturn(UserProfileResponse.builder().email(user.getEmail()).build());

        LoginResponse response = authService.login(loginRequest, httpRequest);

        assertNotNull(response);
        assertEquals("accessToken", response.getAccessToken());
        assertEquals("refreshToken", response.getRefreshToken());
        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(refreshTokenRepository, times(1)).save(argThat(token ->
                "refreshToken".equals(token.getToken())
                        && "127.0.0.1".equals(token.getIpAddress())
                        && "JUnit Browser".equals(token.getUserAgent())
                        && token.getLastUsedAt() != null
        ));
    }

    @Test
    void login_UnverifiedUser_ThrowsBadRequestException() {
        LoginRequest loginRequest = new LoginRequest("test@gmail.com", "password123");

        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(user));

        assertThrows(BadRequestException.class, () -> authService.login(loginRequest, httpRequest));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }
}
