package iuh.fit.se.nextalk_be.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.se.nextalk_be.auth.dto.*;
import iuh.fit.se.nextalk_be.common.ApiResponse;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "APIs for user registration, login, email verification, and token management")
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user account")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        rateLimitService.check("auth:register", rateLimitService.clientIdentity(httpRequest), 5, Duration.ofMinutes(10));
        RegisterResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Registration successful. Please check your email for activation link."));
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Verify account email using token")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success(null, "Email verified successfully. You can now log in."));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and return tokens")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String identity = rateLimitService.clientIdentity(httpRequest) + ":" + request.getEmail();
        rateLimitService.check("auth:login", identity, 8, Duration.ofMinutes(5));
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset email")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        String identity = rateLimitService.clientIdentity(httpRequest) + ":" + request.getEmail();
        rateLimitService.check("auth:forgot-password", identity, 3, Duration.ofMinutes(15));
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password reset email sent (if email exists)"));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using token")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
        rateLimitService.check("auth:reset-password", rateLimitService.clientIdentity(httpRequest), 5, Duration.ofMinutes(15));
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password reset successfully"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Generate new access token using refresh token")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(@Valid @RequestBody TokenRefreshRequest request, HttpServletRequest httpRequest) {
        rateLimitService.check("auth:refresh", rateLimitService.clientIdentity(httpRequest), 30, Duration.ofMinutes(1));
        TokenRefreshResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate refresh token and log user out")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody TokenRefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    @PostMapping("/google-login")
    @Operation(summary = "Authenticate user with Google ID Token")
    public ResponseEntity<ApiResponse<LoginResponse>> googleLogin(@Valid @RequestBody GoogleLoginRequest request, HttpServletRequest httpRequest) {
        rateLimitService.check("auth:google-login", rateLimitService.clientIdentity(httpRequest), 10, Duration.ofMinutes(5));
        return ResponseEntity.ok(ApiResponse.success(authService.googleLogin(request), "Google Login successful"));
    }
}
