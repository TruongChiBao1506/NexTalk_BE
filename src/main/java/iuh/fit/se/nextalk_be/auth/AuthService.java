package iuh.fit.se.nextalk_be.auth;

import iuh.fit.se.nextalk_be.auth.dto.*;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.security.JwtService;
import iuh.fit.se.nextalk_be.user.User;
import iuh.fit.se.nextalk_be.user.UserRepository;
import iuh.fit.se.nextalk_be.user.UserService;
import iuh.fit.se.nextalk_be.user.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailService mailService;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;

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
    public LoginResponse login(LoginRequest request) {
        // Authenticate user
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));

        if (!user.isVerified()) {
            throw new BadRequestException("Account not verified. Please verify your email first.");
        }

        user.setStatus("ONLINE");
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = issueRefreshToken(user);

        UserProfileResponse userProfile = userService.mapToProfileResponse(user);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userProfile)
                .build();
    }

    // @Transactional
    public TokenRefreshResponse refreshToken(TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        RefreshToken token = refreshTokenRepository.findByToken(requestRefreshToken)
                .orElseGet(() -> handleMissingRefreshToken(requestRefreshToken));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new UnauthorizedException("Refresh token was expired. Please sign in again.");
        }

        User user = token.getUser();
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        // Rotate the same token record to avoid a delete/save gap.
        token.setToken(newRefreshToken);
        token.setExpiresAt(refreshTokenExpiresAt());
        refreshTokenRepository.save(token);

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    private String issueRefreshToken(User user) {
        String refreshToken = jwtService.generateRefreshToken(user);
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(refreshTokenExpiresAt())
                .build();
        refreshTokenRepository.save(refreshTokenEntity);
        return refreshToken;
    }

    private LocalDateTime refreshTokenExpiresAt() {
        return LocalDateTime.now().plus(Duration.ofMillis(jwtService.getRefreshExpirationMs()));
    }

    private RefreshToken handleMissingRefreshToken(String requestRefreshToken) {
        try {
            String subject = jwtService.extractUsername(requestRefreshToken);
            userRepository.findByEmail(subject)
                    .or(() -> userRepository.findByUsername(subject))
                    .ifPresent(refreshTokenRepository::deleteByUser);
        } catch (Exception ignored) {
            // Invalid or malformed refresh token. Do not disclose whether it matched an account.
        }

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

    public LoginResponse googleLogin(GoogleLoginRequest request) {
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
                String accessToken = jwtService.generateAccessToken(user);
                String refreshToken = issueRefreshToken(user);

                UserProfileResponse userProfile = userService.mapToProfileResponse(user);

                return LoginResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .user(userProfile)
                        .build();
            } else {
                throw new UnauthorizedException("Invalid Google ID Token");
            }
        } catch (Exception e) {
            throw new UnauthorizedException("Failed to verify Google ID Token: " + e.getMessage());
        }
    }

    // @Transactional
    public void logout(TokenRefreshRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            refreshTokenRepository.findByToken(request.getRefreshToken())
                    .ifPresent(token -> {
                        User user = token.getUser();
                        user.setStatus("OFFLINE");
                        userRepository.save(user);
                        refreshTokenRepository.delete(token);
                    });
        }
    }
}
