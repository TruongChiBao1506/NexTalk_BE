package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.request.ChangePasswordRequest;
import iuh.fit.se.nextalk_be.dto.request.ChatPinRequest;
import iuh.fit.se.nextalk_be.dto.request.UpdateProfileRequest;
import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.UserProfileResponse;
import iuh.fit.se.nextalk_be.dto.response.ProfileQrResponse;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.service.UserService;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "APIs for retrieving and updating user profile information")
public class UserController {

    private final UserService userService;
    private final RateLimitService rateLimitService;

    @GetMapping("/me")
    @Operation(summary = "Get profile of the currently logged-in user")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile() {
        UserProfileResponse response = userService.getMyProfile();
        return ResponseEntity.ok(ApiResponse.success(response, "Profile retrieved successfully"));
    }

    @GetMapping("/qr")
    public ResponseEntity<ApiResponse<ProfileQrResponse>> getProfileQr() {
        return ResponseEntity.ok(ApiResponse.success(userService.getProfileQr(), "Profile QR retrieved"));
    }

    @PostMapping("/qr/rotate")
    public ResponseEntity<ApiResponse<ProfileQrResponse>> rotateProfileQr() {
        rateLimitService.check("profile-qr:rotate", rateLimitService.currentUserIdentity(), 5, Duration.ofHours(1));
        return ResponseEntity.ok(ApiResponse.success(userService.rotateProfileQr(), "Profile QR rotated"));
    }

    @PutMapping("/qr/enabled")
    public ResponseEntity<ApiResponse<ProfileQrResponse>> setProfileQrEnabled(@RequestParam("enabled") boolean enabled) {
        return ResponseEntity.ok(ApiResponse.success(userService.setProfileQrEnabled(enabled), "Profile QR preference updated"));
    }

    @GetMapping("/qr/{token}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> resolveProfileQr(@PathVariable("token") String token) {
        rateLimitService.check("profile-qr:resolve", rateLimitService.currentUserIdentity(), 30, Duration.ofMinutes(1));
        return ResponseEntity.ok(ApiResponse.success(userService.resolveProfileQr(token), "Profile QR resolved"));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update profile fields of the currently logged-in user")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UserProfileResponse response = userService.updateProfile(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Profile updated successfully"));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password for the currently logged-in user")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }

    @GetMapping("/search")
    @Operation(summary = "Search users by email or username (partial match)")
    public ResponseEntity<ApiResponse<java.util.List<UserProfileResponse>>> searchUsers(@RequestParam("query") String query) {
        rateLimitService.check("user:search", rateLimitService.currentUserIdentity(), 60, Duration.ofMinutes(1));
        java.util.List<UserProfileResponse> response = userService.searchUsersByQuery(query);
        return ResponseEntity.ok(ApiResponse.success(response, "Users retrieved successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get profile of a user by their ID")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfileById(@PathVariable("id") String id) {
        UserProfileResponse response = userService.getUserProfileById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "User profile retrieved successfully"));
    }

    @PutMapping("/presence/status")
    @Operation(summary = "Update online status of the currently logged-in user (ONLINE, AWAY, OFFLINE)")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateStatus(@RequestParam("status") String status) {
        UserProfileResponse response = userService.updatePresenceStatus(status);
        return ResponseEntity.ok(ApiResponse.success(response, "Status updated successfully"));
    }

    @PostMapping("/chat-pin/setup")
    @Operation(summary = "Set up a new chat PIN code")
    public ResponseEntity<ApiResponse<UserProfileResponse>> setupChatPin(@Valid @RequestBody ChatPinRequest request) {
        UserProfileResponse response = userService.setupChatPin(request.getPin());
        return ResponseEntity.ok(ApiResponse.success(response, "Chat PIN set up successfully"));
    }

    @PostMapping("/chat-pin/reset")
    @Operation(summary = "Reset chat PIN and clear all hidden conversations history")
    public ResponseEntity<ApiResponse<UserProfileResponse>> resetChatPin(@RequestBody(required = false) ChatPinRequest request) {
        String pin = request != null ? request.getPin() : null;
        UserProfileResponse response = userService.resetChatPin(pin);
        return ResponseEntity.ok(ApiResponse.success(response, "Chat PIN reset successfully"));
    }
}
