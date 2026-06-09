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

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailService mailService;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    @Value("${server.port:8080}")
    private String serverPort;

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
        String verificationLink = "http://localhost:" + serverPort + "/api/auth/verify-email?token=" + token;
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
        String refreshToken = jwtService.generateRefreshToken(user);

        // Save refresh token to DB
        RefreshToken rt = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(rt);

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
                .orElseThrow(() -> new UnauthorizedException("Refresh token is not found in database"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new UnauthorizedException("Refresh token was expired. Please sign in again.");
        }

        User user = token.getUser();
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        // Delete old token and save new one (Token rotation)
        refreshTokenRepository.delete(token);

        RefreshToken newRt = RefreshToken.builder()
                .user(user)
                .token(newRefreshToken)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(newRt);

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
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
