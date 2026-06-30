package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.BlockStatusResponse;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.service.UserBlockService;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
@Tag(name = "User Block", description = "APIs for blocking and unblocking users")
public class UserBlockController {

    private final UserBlockService userBlockService;

    @PostMapping("/{userId}")
    @Operation(summary = "Block a user")
    public ResponseEntity<ApiResponse<BlockStatusResponse>> block(@PathVariable("userId") String userId) {
        BlockStatusResponse response = userBlockService.block(userId);
        return ResponseEntity.ok(ApiResponse.success(response, "User blocked successfully"));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Unblock a user")
    public ResponseEntity<ApiResponse<BlockStatusResponse>> unblock(@PathVariable("userId") String userId) {
        BlockStatusResponse response = userBlockService.unblock(userId);
        return ResponseEntity.ok(ApiResponse.success(response, "User unblocked successfully"));
    }

    @GetMapping("/{userId}/status")
    @Operation(summary = "Get block status with a user")
    public ResponseEntity<ApiResponse<BlockStatusResponse>> status(@PathVariable("userId") String userId) {
        BlockStatusResponse response = userBlockService.getStatus(userId);
        return ResponseEntity.ok(ApiResponse.success(response, "Block status retrieved successfully"));
    }
}
