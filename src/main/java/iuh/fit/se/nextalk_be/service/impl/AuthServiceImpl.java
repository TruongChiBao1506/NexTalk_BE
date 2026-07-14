package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.AuthService;

import iuh.fit.se.nextalk_be.dto.request.ForgotPasswordRequest;
import iuh.fit.se.nextalk_be.dto.request.GoogleLoginRequest;
import iuh.fit.se.nextalk_be.dto.request.LoginRequest;
import iuh.fit.se.nextalk_be.dto.request.QrLoginConfirmRequest;
import iuh.fit.se.nextalk_be.dto.request.RegisterRequest;
import iuh.fit.se.nextalk_be.dto.request.ResetPasswordRequest;
import iuh.fit.se.nextalk_be.dto.request.TokenRefreshRequest;
import iuh.fit.se.nextalk_be.dto.response.LoginResponse;
import iuh.fit.se.nextalk_be.dto.response.QrLoginInitResponse;
import iuh.fit.se.nextalk_be.dto.response.QrLoginStatusResponse;
import iuh.fit.se.nextalk_be.dto.response.RegisterResponse;
import iuh.fit.se.nextalk_be.dto.response.SessionResponse;
import iuh.fit.se.nextalk_be.dto.response.TokenRefreshResponse;
import iuh.fit.se.nextalk_be.dto.response.UserProfileResponse;
import iuh.fit.se.nextalk_be.entity.EmailVerification;
import iuh.fit.se.nextalk_be.entity.PasswordResetToken;
import iuh.fit.se.nextalk_be.entity.QrLoginSession;
import iuh.fit.se.nextalk_be.entity.QrLoginStatus;
import iuh.fit.se.nextalk_be.entity.RefreshToken;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.repository.EmailVerificationRepository;
import iuh.fit.se.nextalk_be.repository.PasswordResetTokenRepository;
import iuh.fit.se.nextalk_be.repository.QrLoginSessionRepository;
import iuh.fit.se.nextalk_be.repository.RefreshTokenRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.security.JwtService;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.service.MailService;
import iuh.fit.se.nextalk_be.service.UserService;
import iuh.fit.se.nextalk_be.service.WebSocketSessionRegistry;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final QrLoginSessionRepository qrLoginSessionRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailService mailService;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final RateLimitService rateLimitService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${google.client.id}")
    private String googleClientId;

    // @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken");
        }

        User user = User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .status("OFFLINE")
                .isVerified(false)
                .build();

        User savedUser = userRepository.save(user);

        // Generate email verification token
        String token = UUID.randomUUID().toString();
        EmailVerification verification = EmailVerification.builder()
                .user(savedUser)
                .token(token)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .verified(false)
                .build();

        emailVerificationRepository.save(verification);

        // Send email
        String verificationLink = "http://localhost:3000/verify-email?token=" + token;
        mailService.sendVerificationEmail(savedUser.getEmail(), verificationLink);

        return RegisterResponse.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .username(savedUser.getUsername())
                .isVerified(savedUser.isVerified())
                .build();
    }

    // @Transactional
    public void verifyEmail(String token) {
        EmailVerification verification = emailVerificationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Verification token not found"));

        if (verification.isVerified()) {
            throw new BadRequestException("Email is already verified");
        }

        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Verification token has expired");
        }

        User user = verification.getUser();
        user.setVerified(true);
        userRepository.save(user);

        verification.setVerified(true);
        emailVerificationRepository.save(verification);
    }

    // @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        // Authenticate user
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));

        if (!user.isVerified()) {
            throw new BadRequestException("Account not verified. Please verify your email first.");
        }

        IssuedSession issuedSession = issueRefreshToken(user, httpRequest);
        String accessToken = jwtService.generateAccessToken(user, issuedSession.session().getId());

        UserProfileResponse userProfile = userService.mapToProfileResponse(user);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(issuedSession.refreshToken())
                .user(userProfile)
                .build();
    }

    // @Transactional
    public TokenRefreshResponse refreshToken(TokenRefreshRequest request, HttpServletRequest httpRequest) {
        String requestRefreshToken = request.getRefreshToken();

        RefreshToken token = refreshTokenRepository.findByToken(requestRefreshToken)
                .orElseGet(() -> handleMissingRefreshToken(requestRefreshToken));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new UnauthorizedException("Refresh token was expired. Please sign in again.");
        }

        User user = token.getUser();
        String newAccessToken = jwtService.generateAccessToken(user, token.getId());
        String newRefreshToken = jwtService.generateRefreshToken(user);

        // Rotate the same token record to avoid a delete/save gap.
        token.setToken(newRefreshToken);
        token.setExpiresAt(refreshTokenExpiresAt());
        token.setLastUsedAt(LocalDateTime.now());
        token.setIpAddress(resolveIpAddress(httpRequest));
        token.setUserAgent(resolveUserAgent(httpRequest));
        refreshTokenRepository.save(token);

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    private IssuedSession issueRefreshToken(User user, HttpServletRequest httpRequest) {
        String refreshToken = jwtService.generateRefreshToken(user);
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(refreshTokenExpiresAt())
                .ipAddress(resolveIpAddress(httpRequest))
                .userAgent(resolveUserAgent(httpRequest))
                .lastUsedAt(LocalDateTime.now())
                .build();
        RefreshToken savedSession = refreshTokenRepository.save(refreshTokenEntity);
        return new IssuedSession(savedSession, refreshToken);
    }

    private record IssuedSession(RefreshToken session, String refreshToken) {}

    private LocalDateTime refreshTokenExpiresAt() {
        return LocalDateTime.now().plus(Duration.ofMillis(jwtService.getRefreshExpirationMs()));
    }

    private RefreshToken handleMissingRefreshToken(String requestRefreshToken) {
        // A missing token belongs to one revoked/rotated session. Never revoke every
        // session for the account here, otherwise one stale device logs out all others.
        throw new UnauthorizedException("Refresh token was reused or revoked. Please sign in again.");
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .used(false)
                .build();

        passwordResetTokenRepository.save(resetToken);

        String resetLink = "http://localhost:3000/reset-password?token=" + token;
        if ("mobile".equalsIgnoreCase(request.getClient()) && request.getReturnUrl() != null) {
            HttpServletRequest httpRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            String baseUrl = httpRequest.getScheme() + "://" + httpRequest.getServerName() + ":" + httpRequest.getServerPort();
            resetLink = baseUrl + "/api/auth/mobile-reset?returnUrl=" + request.getReturnUrl() + "&token=" + token;
        }

        mailService.sendPasswordResetEmail(user.getEmail(), resetLink);
    }

    // @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired reset token"));

        if (resetToken.isUsed()) {
            throw new BadRequestException("Token has already been used");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }

    public LoginResponse googleLogin(GoogleLoginRequest request, HttpServletRequest httpRequest) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(java.util.Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(request.getIdToken());
            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();
                String email = payload.getEmail();
                String name = (String) payload.get("name");
                String pictureUrl = (String) payload.get("picture");

                User user = userRepository.findByEmail(email).orElse(null);
                if (user == null) {
                    // Create new user
                    user = User.builder()
                            .email(email)
                            .username(name.replaceAll("\\s+", "").toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 5))
                            .password(passwordEncoder.encode(UUID.randomUUID().toString())) // Random password
                            .avatarUrl(pictureUrl)
                            .status("OFFLINE")
                            .isVerified(true)
                            .build();
                    user = userRepository.save(user);
                } else {
                    // Update user details if needed
                    boolean updated = false;
                    if (user.getAvatarUrl() == null || user.getAvatarUrl().isEmpty()) {
                        user.setAvatarUrl(pictureUrl);
                        updated = true;
                    }
                    if (!user.isVerified()) {
                        user.setVerified(true);
                        updated = true;
                    }
                    if (updated) {
                        user = userRepository.save(user);
                    }
                }

                // Generate tokens
                IssuedSession issuedSession = issueRefreshToken(user, httpRequest);
                String accessToken = jwtService.generateAccessToken(user, issuedSession.session().getId());

                UserProfileResponse userProfile = userService.mapToProfileResponse(user);

                return LoginResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(issuedSession.refreshToken())
                        .user(userProfile)
                        .build();
            } else {
                throw new UnauthorizedException("Invalid Google ID Token");
            }
        } catch (Exception e) {
            throw new UnauthorizedException("Failed to verify Google ID Token: " + e.getMessage());
        }
    }

    public QrLoginInitResponse initQrLogin(HttpServletRequest httpRequest) {
        QrLoginSession session = QrLoginSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .qrToken(UUID.randomUUID() + "-" + UUID.randomUUID())
                .status(QrLoginStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(2))
                .ipAddress(resolveIpAddress(httpRequest))
                .userAgent(resolveUserAgent(httpRequest))
                .build();

        QrLoginSession savedSession = qrLoginSessionRepository.save(session);
        return QrLoginInitResponse.builder()
                .sessionId(savedSession.getSessionId())
                .qrToken(savedSession.getQrToken())
                .expiresAt(savedSession.getExpiresAt())
                .build();
    }

    public QrLoginStatusResponse getQrLoginStatus(String sessionId, HttpServletRequest httpRequest) {
        QrLoginSession session = qrLoginSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("QR login session not found"));

        if (isQrSessionExpired(session)) {
            return saveQrStatus(session, QrLoginStatus.EXPIRED, null);
        }

        if (session.getStatus() != QrLoginStatus.CONFIRMED) {
            return QrLoginStatusResponse.builder()
                    .status(session.getStatus())
                    .expiresAt(session.getExpiresAt())
                    .build();
        }

        User user = session.getUser();
        if (user == null) {
            return saveQrStatus(session, QrLoginStatus.EXPIRED, null);
        }

        IssuedSession issuedSession = issueRefreshToken(user, httpRequest);
        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken(jwtService.generateAccessToken(user, issuedSession.session().getId()))
                .refreshToken(issuedSession.refreshToken())
                .user(userService.mapToProfileResponse(user))
                .build();

        session.setStatus(QrLoginStatus.CONSUMED);
        session.setConsumedAt(LocalDateTime.now());
        qrLoginSessionRepository.save(session);

        return QrLoginStatusResponse.builder()
                .status(QrLoginStatus.CONFIRMED)
                .expiresAt(session.getExpiresAt())
                .login(loginResponse)
                .build();
    }

    public QrLoginStatusResponse confirmQrLogin(QrLoginConfirmRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        QrLoginSession session = qrLoginSessionRepository.findByQrToken(request.getQrToken())
                .orElseThrow(() -> new ResourceNotFoundException("QR login session not found"));

        if (isQrSessionExpired(session)) {
            return saveQrStatus(session, QrLoginStatus.EXPIRED, null);
        }

        if (session.getStatus() == QrLoginStatus.CONFIRMED || session.getStatus() == QrLoginStatus.CONSUMED) {
            throw new BadRequestException("QR login session was already used");
        }

        session.setUser(currentUser);
        session.setStatus(QrLoginStatus.CONFIRMED);
        session.setConfirmedAt(LocalDateTime.now());
        qrLoginSessionRepository.save(session);

        return QrLoginStatusResponse.builder()
                .status(QrLoginStatus.CONFIRMED)
                .expiresAt(session.getExpiresAt())
                .build();
    }

    private boolean isQrSessionExpired(QrLoginSession session) {
        return session.getExpiresAt() == null
                || session.getExpiresAt().isBefore(LocalDateTime.now())
                || session.getStatus() == QrLoginStatus.EXPIRED;
    }

    private QrLoginStatusResponse saveQrStatus(QrLoginSession session, QrLoginStatus status, LoginResponse loginResponse) {
        if (session.getStatus() != status) {
            session.setStatus(status);
            qrLoginSessionRepository.save(session);
        }
        return QrLoginStatusResponse.builder()
                .status(status)
                .expiresAt(session.getExpiresAt())
                .login(loginResponse)
                .build();
    }

    // @Transactional
    public void logout(TokenRefreshRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            refreshTokenRepository.findByToken(request.getRefreshToken())
                    .ifPresent(token -> {
                        removeSessionFcmToken(token);
                        refreshTokenRepository.delete(token);
                    });
        }
    }

    public List<SessionResponse> getSessions() {
        User currentUser = userService.getCurrentAuthenticatedUser();
        return refreshTokenRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(this::mapToSessionResponse)
                .toList();
    }

    public void revokeSession(String id) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        RefreshToken session = refreshTokenRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        removeSessionFcmToken(session);
        refreshTokenRepository.delete(session);
        messagingTemplate.convertAndSendToUser(
                currentUser.getUsername(),
                "/queue/private",
                Map.of("type", "SESSION_REVOKED", "sessionId", id)
        );
        webSocketSessionRegistry.closeLoginSession(id);
    }

    public void revokeAllSessions() {
        User currentUser = userService.getCurrentAuthenticatedUser();
        List<RefreshToken> sessions = refreshTokenRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId());
        sessions.forEach(this::removeSessionFcmToken);
        refreshTokenRepository.deleteByUser(currentUser);
        messagingTemplate.convertAndSendToUser(
                currentUser.getUsername(),
                "/queue/private",
                Map.of("type", "ALL_SESSIONS_REVOKED")
        );
        webSocketSessionRegistry.closeLoginSessions(sessions.stream().map(RefreshToken::getId).toList());
    }

    private void removeSessionFcmToken(RefreshToken session) {
        if (session.getFcmToken() == null || session.getFcmToken().isBlank() || session.getUser() == null) {
            return;
        }
        userRepository.findById(session.getUser().getId()).ifPresent(user -> {
            if (user.getFcmTokens() != null && user.getFcmTokens().remove(session.getFcmToken())) {
                userRepository.save(user);
            }
        });
    }

    private SessionResponse mapToSessionResponse(RefreshToken token) {
        return SessionResponse.builder()
                .id(token.getId())
                .deviceName(resolveDeviceName(token.getUserAgent()))
                .userAgent(token.getUserAgent())
                .ipAddress(token.getIpAddress())
                .createdAt(token.getCreatedAt())
                .lastUsedAt(token.getLastUsedAt())
                .expiresAt(token.getExpiresAt())
                .build();
    }

    private String resolveIpAddress(HttpServletRequest request) {
        return rateLimitService.clientIdentity(request);
    }

    private String resolveUserAgent(HttpServletRequest request) {
        if (request == null) {
            return "Unknown device";
        }
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        return userAgent.length() > 300 ? userAgent.substring(0, 300) : userAgent;
    }

    private String resolveDeviceName(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        String ua = userAgent.toLowerCase();
        String browser = ua.contains("edg/") ? "Edge"
                : ua.contains("chrome/") ? "Chrome"
                : ua.contains("firefox/") ? "Firefox"
                : ua.contains("safari/") ? "Safari"
                : "Browser";
        String platform = ua.contains("windows") ? "Windows"
                : ua.contains("mac os") ? "macOS"
                : ua.contains("android") ? "Android"
                : ua.contains("iphone") || ua.contains("ipad") ? "iOS"
                : ua.contains("linux") ? "Linux"
                : "Unknown OS";
        return browser + " on " + platform;
    }
}
