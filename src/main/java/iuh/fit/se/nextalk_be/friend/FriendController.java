package iuh.fit.se.nextalk_be.friend;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.se.nextalk_be.common.ApiResponse;
import iuh.fit.se.nextalk_be.friend.dto.FriendResponse;
import iuh.fit.se.nextalk_be.friend.dto.FriendshipAcceptRequest;
import iuh.fit.se.nextalk_be.friend.dto.FriendshipRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
@Tag(name = "Friend System", description = "APIs for managing friend relationships and invitations")
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/request")
    @Operation(summary = "Send a friend request to another user")
    public ResponseEntity<ApiResponse<Void>> sendFriendRequest(@Valid @RequestBody FriendshipRequest request) {
        friendService.sendFriendRequest(request.getReceiverId());
        return ResponseEntity.ok(ApiResponse.success(null, "Friend request sent successfully"));
    }

    @PutMapping("/accept")
    @Operation(summary = "Accept a pending friend request")
    public ResponseEntity<ApiResponse<Void>> acceptFriendRequest(@Valid @RequestBody FriendshipAcceptRequest request) {
        friendService.acceptFriendRequest(request.getSenderId());
        return ResponseEntity.ok(ApiResponse.success(null, "Friend request accepted successfully"));
    }

    @PutMapping("/reject")
    @Operation(summary = "Reject a pending friend request")
    public ResponseEntity<ApiResponse<Void>> rejectFriendRequest(@Valid @RequestBody FriendshipAcceptRequest request) {
        friendService.rejectFriendRequest(request.getSenderId());
        return ResponseEntity.ok(ApiResponse.success(null, "Friend request rejected successfully"));
    }

    @DeleteMapping("/remove")
    @Operation(summary = "Remove an existing friend relationship")
    public ResponseEntity<ApiResponse<Void>> removeFriend(@RequestParam("friendId") String friendId) {
        friendService.removeFriend(friendId);
        return ResponseEntity.ok(ApiResponse.success(null, "Friend removed successfully"));
    }

    @GetMapping
    @Operation(summary = "Retrieve accepted friends list")
    public ResponseEntity<ApiResponse<List<FriendResponse>>> getFriendsList() {
        List<FriendResponse> friends = friendService.getFriendsList();
        return ResponseEntity.ok(ApiResponse.success(friends, "Friends list retrieved successfully"));
    }

    @GetMapping("/pending")
    @Operation(summary = "Retrieve received pending friend requests")
    public ResponseEntity<ApiResponse<List<FriendResponse>>> getPendingRequests() {
        List<FriendResponse> pending = friendService.getPendingRequests();
        return ResponseEntity.ok(ApiResponse.success(pending, "Pending requests retrieved successfully"));
    }
}
