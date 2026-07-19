package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.request.ForgotPasswordRequest;
import iuh.fit.se.nextalk_be.dto.request.GoogleLoginRequest;
import iuh.fit.se.nextalk_be.dto.request.LoginRequest;
import iuh.fit.se.nextalk_be.dto.request.QrLoginConfirmRequest;
import iuh.fit.se.nextalk_be.dto.request.RegisterRequest;
import iuh.fit.se.nextalk_be.dto.request.ResetPasswordRequest;
import iuh.fit.se.nextalk_be.dto.request.TokenRefreshRequest;
import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.LoginResponse;
import iuh.fit.se.nextalk_be.dto.response.QrLoginInitResponse;
import iuh.fit.se.nextalk_be.dto.response.QrLoginStatusResponse;
import iuh.fit.se.nextalk_be.dto.response.RegisterResponse;
import iuh.fit.se.nextalk_be.dto.response.SessionResponse;
import iuh.fit.se.nextalk_be.dto.response.TokenRefreshResponse;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.service.AuthService;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
import java.net.URI;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "APIs for user registration, login, email verification, and token management")
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;

    @Value("${app.mobile.reset-return-url:nextalk://reset-password}")
    private String mobileResetReturnUrl;

    @Value("${app.auth.refresh-cookie-secure:true}")
    private boolean refreshCookieSecure;

    @Value("${app.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    @Value("${app.auth.refresh-cookie-same-site:None}")
    private String refreshCookieSameSite;

    @Value("${app.auth.refresh-cookie-partitioned:true}")
    private boolean refreshCookiePartitioned;

    private static final String REFRESH_COOKIE = "nextalk_refresh";

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
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
                                                             @RequestHeader(value = "X-Client-Platform", required = false) String platform) {
        String identity = rateLimitService.clientIdentity(httpRequest) + ":" + request.getEmail();
        rateLimitService.check("auth:login", identity, 8, Duration.ofMinutes(5));
        LoginResponse response = authService.login(request, httpRequest);
        return loginResponse(response, platform, "Login successful");
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset email")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        String identity = rateLimitService.clientIdentity(httpRequest) + ":" + request.getEmail();
        rateLimitService.check("auth:forgot-password", identity, 3, Duration.ofMinutes(15));
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password reset email sent (if email exists)"));
    }

    @GetMapping("/mobile-reset")
    @Operation(summary = "Redirect mobile deep link")
    public ResponseEntity<Void> mobileReset(@RequestParam String token) {
        URI destination = org.springframework.web.util.UriComponentsBuilder
                .fromUriString(mobileResetReturnUrl)
                .queryParam("token", token)
                .build()
                .encode()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, destination.toString())
                .build();
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
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(@RequestBody(required = false) TokenRefreshRequest request,
                                                                      @CookieValue(value = REFRESH_COOKIE, required = false) String cookieToken,
                                                                      HttpServletRequest httpRequest,
                                                                      @RequestHeader(value = "X-Client-Platform", required = false) String platform) {
        rateLimitService.check("auth:refresh", rateLimitService.clientIdentity(httpRequest), 30, Duration.ofMinutes(1));
        TokenRefreshRequest resolved = resolveRefreshRequest(request, cookieToken);
        TokenRefreshResponse response = authService.refreshToken(resolved, httpRequest);
        if (isWeb(platform)) {
            String rotatedToken = response.getRefreshToken();
            response.setRefreshToken(null);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookie(rotatedToken).toString())
                    .body(ApiResponse.success(response, "Token refreshed successfully"));
        }
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate refresh token and log user out")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) TokenRefreshRequest request,
                                                     @CookieValue(value = REFRESH_COOKIE, required = false) String cookieToken) {
        authService.logout(resolveRefreshRequest(request, cookieToken));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body(ApiResponse.success(null, "Logged out successfully"));
    }

    @PostMapping("/google-login")
    @Operation(summary = "Authenticate user with Google ID Token")
    public ResponseEntity<ApiResponse<LoginResponse>> googleLogin(@Valid @RequestBody GoogleLoginRequest request, HttpServletRequest httpRequest,
                                                                   @RequestHeader(value = "X-Client-Platform", required = false) String platform) {
        rateLimitService.check("auth:google-login", rateLimitService.clientIdentity(httpRequest), 10, Duration.ofMinutes(5));
        return loginResponse(authService.googleLogin(request, httpRequest), platform, "Google Login successful");
    }

    @PostMapping("/qr/init")
    @Operation(summary = "Create a short-lived QR login session")
    public ResponseEntity<ApiResponse<QrLoginInitResponse>> initQrLogin(HttpServletRequest httpRequest) {
        rateLimitService.check("auth:qr:init", rateLimitService.clientIdentity(httpRequest), 10, Duration.ofMinutes(5));
        return ResponseEntity.ok(ApiResponse.success(authService.initQrLogin(httpRequest), "QR login session created"));
    }

    @GetMapping("/qr/status/{sessionId}")
    @Operation(summary = "Poll QR login session status")
    public ResponseEntity<ApiResponse<QrLoginStatusResponse>> getQrLoginStatus(@PathVariable("sessionId") String sessionId, HttpServletRequest httpRequest,
                                                                                @RequestHeader(value = "X-Client-Platform", required = false) String platform) {
        rateLimitService.check("auth:qr:status", rateLimitService.clientIdentity(httpRequest) + ":" + sessionId, 80, Duration.ofMinutes(2));
        QrLoginStatusResponse response = authService.getQrLoginStatus(sessionId, httpRequest);
        if (isWeb(platform) && response.getLogin() != null && response.getLogin().getRefreshToken() != null) {
            String token = response.getLogin().getRefreshToken();
            response.getLogin().setRefreshToken(null);
            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie(token).toString())
                    .body(ApiResponse.success(response, "QR login status retrieved"));
        }
        return ResponseEntity.ok(ApiResponse.success(response, "QR login status retrieved"));
    }

    @PostMapping("/qr/confirm")
    @Operation(summary = "Confirm a QR login session as the current user")
    public ResponseEntity<ApiResponse<QrLoginStatusResponse>> confirmQrLogin(@Valid @RequestBody QrLoginConfirmRequest request) {
        rateLimitService.check("auth:qr:confirm", rateLimitService.currentUserIdentity(), 10, Duration.ofMinutes(1));
        return ResponseEntity.ok(ApiResponse.success(authService.confirmQrLogin(request), "QR login confirmed"));
    }

    @GetMapping("/sessions")
    @Operation(summary = "List active login sessions for current user")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getSessions() {
        return ResponseEntity.ok(ApiResponse.success(authService.getSessions(), "Sessions retrieved successfully"));
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "Revoke a login session by ID")
    public ResponseEntity<ApiResponse<Void>> revokeSession(@PathVariable("id") String id) {
        authService.revokeSession(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Session revoked successfully"));
    }

    @DeleteMapping("/sessions")
    @Operation(summary = "Revoke all login sessions for current user")
    public ResponseEntity<ApiResponse<Void>> revokeAllSessions() {
        authService.revokeAllSessions();
        return ResponseEntity.ok(ApiResponse.success(null, "All sessions revoked successfully"));
    }

    private ResponseEntity<ApiResponse<LoginResponse>> loginResponse(LoginResponse response, String platform, String message) {
        if (!isWeb(platform)) return ResponseEntity.ok(ApiResponse.success(response, message));
        String token = response.getRefreshToken();
        response.setRefreshToken(null);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie(token).toString())
                .body(ApiResponse.success(response, message));
    }

    private TokenRefreshRequest resolveRefreshRequest(TokenRefreshRequest request, String cookieToken) {
        String bodyToken = request != null ? request.getRefreshToken() : null;
        String token = StringUtils.hasText(bodyToken) ? bodyToken : cookieToken;
        if (!StringUtils.hasText(token)) throw new iuh.fit.se.nextalk_be.exception.UnauthorizedException("Refresh token is required");
        return TokenRefreshRequest.builder().refreshToken(token).build();
    }

    private boolean isWeb(String platform) {
        return "web".equalsIgnoreCase(platform);
    }

    private ResponseCookie refreshCookie(String token) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true).secure(refreshCookieSecure).sameSite(refreshCookieSameSite)
                .partitioned(refreshCookiePartitioned)
                .path("/api/auth").maxAge(Duration.ofMillis(refreshExpirationMs)).build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(refreshCookieSecure).sameSite(refreshCookieSameSite)
                .partitioned(refreshCookiePartitioned)
                .path("/api/auth").maxAge(Duration.ZERO).build();
    }
}
