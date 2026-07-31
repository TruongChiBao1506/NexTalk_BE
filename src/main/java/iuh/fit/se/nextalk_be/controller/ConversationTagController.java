package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.request.AssignConversationTagRequest;
import iuh.fit.se.nextalk_be.dto.request.CreateConversationTagRequest;
import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.ConversationTagDataResponse;
import iuh.fit.se.nextalk_be.dto.response.ConversationTagResponse;
import iuh.fit.se.nextalk_be.service.ConversationTagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversation-tags")
@RequiredArgsConstructor
public class ConversationTagController {

    private final ConversationTagService tagService;

    @GetMapping
    public ResponseEntity<ApiResponse<ConversationTagDataResponse>> getUserTagData() {
        return ResponseEntity.ok(ApiResponse.success(tagService.getUserTagData(), "User tag data retrieved"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationTagResponse>> createTag(
            @Valid @RequestBody CreateConversationTagRequest request) {
        return ResponseEntity.ok(ApiResponse.success(tagService.createTag(request), "Conversation tag created"));
    }

    @PutMapping("/{tagId}")
    public ResponseEntity<ApiResponse<ConversationTagResponse>> updateTag(
            @PathVariable String tagId,
            @Valid @RequestBody CreateConversationTagRequest request) {
        return ResponseEntity.ok(ApiResponse.success(tagService.updateTag(tagId, request), "Conversation tag updated"));
    }

    @DeleteMapping("/{tagId}")
    public ResponseEntity<ApiResponse<Void>> deleteTag(@PathVariable String tagId) {
        tagService.deleteTag(tagId);
        return ResponseEntity.ok(ApiResponse.success(null, "Conversation tag deleted"));
    }

    @PostMapping("/{tagId}/assign")
    public ResponseEntity<ApiResponse<ConversationTagDataResponse>> assignTag(
            @PathVariable String tagId,
            @Valid @RequestBody AssignConversationTagRequest request) {
        return ResponseEntity.ok(ApiResponse.success(tagService.assignTag(tagId, request), "Tag assigned to conversation"));
    }

    @DeleteMapping("/{tagId}/assign/{targetId}")
    public ResponseEntity<ApiResponse<ConversationTagDataResponse>> unassignTag(
            @PathVariable String tagId,
            @PathVariable String targetId) {
        return ResponseEntity.ok(ApiResponse.success(tagService.unassignTag(tagId, targetId), "Tag removed from conversation"));
    }
}
