package iuh.fit.se.nextalk_be.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.se.nextalk_be.common.ApiResponse;
import iuh.fit.se.nextalk_be.user.dto.UpdateProfileRequest;
import iuh.fit.se.nextalk_be.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "APIs for retrieving and updating user profile information")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get profile of the currently logged-in user")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile() {
        UserProfileResponse response = userService.getMyProfile();
        return ResponseEntity.ok(ApiResponse.success(response, "Profile retrieved successfully"));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update profile fields of the currently logged-in user")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UserProfileResponse response = userService.updateProfile(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Profile updated successfully"));
    }

    @GetMapping("/search")
    @Operation(summary = "Search users by email or username (partial match)")
    public ResponseEntity<ApiResponse<java.util.List<UserProfileResponse>>> searchUsers(@RequestParam("query") String query) {
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
}
