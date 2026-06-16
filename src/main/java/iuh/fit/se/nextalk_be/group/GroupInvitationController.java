package iuh.fit.se.nextalk_be.group;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.se.nextalk_be.common.ApiResponse;
import iuh.fit.se.nextalk_be.group.dto.GroupInvitationResponse;
import iuh.fit.se.nextalk_be.group.dto.InviteUserRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Tag(name = "Group Invitations", description = "APIs for group invitations and approvals")
public class GroupInvitationController {

    private final GroupInvitationService groupInvitationService;

    @PostMapping("/{id}/invites")
    @Operation(summary = "Invite a user to the group")
    public ResponseEntity<ApiResponse<Void>> inviteMember(
            @PathVariable("id") String id,
            @Valid @RequestBody InviteUserRequest request) {
        groupInvitationService.inviteMember(id, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Invitation sent successfully"));
    }

    @GetMapping("/invites/pending")
    @Operation(summary = "Get all pending invitations for the current user")
    public ResponseEntity<ApiResponse<List<GroupInvitationResponse>>> getPendingInvitations() {
        List<GroupInvitationResponse> response = groupInvitationService.getMyPendingInvitations();
        return ResponseEntity.ok(ApiResponse.success(response, "Pending invitations retrieved successfully"));
    }

    @GetMapping("/{id}/invites/waiting")
    @Operation(summary = "Get all invitations waiting for admin approval (admin only)")
    public ResponseEntity<ApiResponse<List<GroupInvitationResponse>>> getWaitingApprovals(
            @PathVariable("id") String id) {
        List<GroupInvitationResponse> response = groupInvitationService.getWaitingApprovals(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Waiting approvals retrieved successfully"));
    }

    @PostMapping("/invites/{inviteId}/accept")
    @Operation(summary = "Accept an invitation")
    public ResponseEntity<ApiResponse<Void>> acceptInvitation(@PathVariable("inviteId") String inviteId) {
        groupInvitationService.acceptInvitation(inviteId);
        return ResponseEntity.ok(ApiResponse.success(null, "Invitation accepted successfully"));
    }

    @PostMapping("/invites/{inviteId}/reject")
    @Operation(summary = "Reject an invitation")
    public ResponseEntity<ApiResponse<Void>> rejectInvitation(@PathVariable("inviteId") String inviteId) {
        groupInvitationService.rejectInvitation(inviteId);
        return ResponseEntity.ok(ApiResponse.success(null, "Invitation rejected successfully"));
    }

    @PostMapping("/invites/{inviteId}/approve")
    @Operation(summary = "Approve a member (admin only)")
    public ResponseEntity<ApiResponse<Void>> approveMember(@PathVariable("inviteId") String inviteId) {
        groupInvitationService.approveMember(inviteId);
        return ResponseEntity.ok(ApiResponse.success(null, "Member approved successfully"));
    }

    @PostMapping("/invites/{inviteId}/decline")
    @Operation(summary = "Decline a member (admin only)")
    public ResponseEntity<ApiResponse<Void>> declineMember(@PathVariable("inviteId") String inviteId) {
        groupInvitationService.declineMember(inviteId);
        return ResponseEntity.ok(ApiResponse.success(null, "Member declined successfully"));
    }
}
