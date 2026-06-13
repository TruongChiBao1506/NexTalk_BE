package iuh.fit.se.nextalk_be.group;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.se.nextalk_be.common.ApiResponse;
import iuh.fit.se.nextalk_be.group.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Tag(name = "Group Management", description = "APIs for creating and managing group chats")
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    @Operation(summary = "Create a new group chat")
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        GroupResponse response = groupService.createGroup(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Group created successfully"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update group name (owner or admin only)")
    public ResponseEntity<ApiResponse<GroupResponse>> updateGroup(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateGroupRequest request) {
        GroupResponse response = groupService.updateGroup(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Group updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete group (owner only)")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable("id") String id) {
        groupService.deleteGroup(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Group deleted successfully"));
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Add a member to the group (owner or admin only)")
    public ResponseEntity<ApiResponse<GroupResponse>> addMember(
            @PathVariable("id") String id,
            @Valid @RequestBody AddMemberRequest request) {
        GroupResponse response = groupService.addMember(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Member added successfully"));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Operation(summary = "Remove a member from the group")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable("id") String id,
            @PathVariable("userId") String userId) {
        groupService.removeMember(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Member removed successfully"));
    }

    @PutMapping("/{id}/members/{userId}/role")
    @Operation(summary = "Update member role (owner or leader only)")
    public ResponseEntity<ApiResponse<GroupResponse>> updateMemberRole(
            @PathVariable("id") String id,
            @PathVariable("userId") String userId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        GroupResponse response = groupService.updateMemberRole(id, userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Member role updated successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get group details")
    public ResponseEntity<ApiResponse<GroupResponse>> getGroup(@PathVariable("id") String id) {
        GroupResponse response = groupService.getGroupById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Group retrieved successfully"));
    }

    @GetMapping
    @Operation(summary = "Get all groups for the currently logged-in user")
    public ResponseEntity<ApiResponse<List<GroupResponse>>> getMyGroups() {
        List<GroupResponse> response = groupService.getMyGroups();
        return ResponseEntity.ok(ApiResponse.success(response, "Groups retrieved successfully"));
    }
}
