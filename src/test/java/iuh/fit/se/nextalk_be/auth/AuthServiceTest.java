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
import iuh.fit.se.nextalk_be.repository.QrLoginSessionRepository;
import iuh.fit.se.nextalk_be.repository.PasswordResetTokenRepository;
import iuh.fit.se.nextalk_be.security.JwtService;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.service.impl.AuthServiceImpl;
import iuh.fit.se.nextalk_be.service.WebSocketSessionRegistry;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
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
    private QrLoginSessionRepository qrLoginSessionRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

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
    private RateLimitService rateLimitService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private WebSocketSessionRegistry webSocketSessionRegistry;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthServiceImpl authService;

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
        when(jwtService.generateRefreshToken(user)).thenReturn("refreshToken");
        when(jwtService.getRefreshExpirationMs()).thenReturn(604800000L);
        when(rateLimitService.clientIdentity(httpRequest)).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit Browser");
        when(userService.mapToProfileResponse(user)).thenReturn(UserProfileResponse.builder().email(user.getEmail()).build());
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            token.setId("session-1");
            return token;
        });
        when(jwtService.generateAccessToken(user, "session-1")).thenReturn("accessToken");

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

    @Test
    void revokeSession_RemovesOnlyTargetSessionAndItsFcmToken() {
        user.setFcmTokens(new java.util.ArrayList<>(List.of("fcm-a", "fcm-b")));
        RefreshToken session = RefreshToken.builder().user(user).token("refresh-a").fcmToken("fcm-a").build();
        session.setId("session-a");

        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);
        when(refreshTokenRepository.findByIdAndUserId("session-a", user.getId())).thenReturn(Optional.of(session));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        authService.revokeSession("session-a");

        assertEquals(List.of("fcm-b"), user.getFcmTokens());
        verify(refreshTokenRepository).delete(session);
        verify(refreshTokenRepository, never()).deleteByUser(any());
        verify(messagingTemplate).convertAndSendToUser(eq(user.getUsername()), eq("/queue/private"), any());
        verify(webSocketSessionRegistry).closeLoginSession("session-a");
    }

    @Test
    void revokeAllSessions_RemovesEverySessionAndFcmToken() {
        user.setFcmTokens(new java.util.ArrayList<>(List.of("fcm-a", "fcm-b")));
        RefreshToken first = RefreshToken.builder().user(user).fcmToken("fcm-a").build();
        RefreshToken second = RefreshToken.builder().user(user).fcmToken("fcm-b").build();

        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);
        when(refreshTokenRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of(first, second));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        authService.revokeAllSessions();

        assertTrue(user.getFcmTokens().isEmpty());
        verify(refreshTokenRepository).deleteByUser(user);
        verify(messagingTemplate).convertAndSendToUser(eq(user.getUsername()), eq("/queue/private"), any());
        verify(webSocketSessionRegistry).closeLoginSessions(any());
    }
}
