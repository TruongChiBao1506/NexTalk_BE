package iuh.fit.se.nextalk_be.conversation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.se.nextalk_be.common.ApiResponse;
import iuh.fit.se.nextalk_be.conversation.dto.ConversationResponse;
import iuh.fit.se.nextalk_be.conversation.dto.UpdateSelfDestructRequest;
import iuh.fit.se.nextalk_be.summary.ConversationSummaryService;
import iuh.fit.se.nextalk_be.summary.dto.ConversationSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversation Management", description = "APIs for creating and listing chat conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationSummaryService conversationSummaryService;

    @PostMapping("/private/{friendId}")
    @Operation(summary = "Get or create a private conversation with a friend")
    public ResponseEntity<ApiResponse<ConversationResponse>> getOrCreatePrivateConversation(@PathVariable("friendId") String friendId) {
        ConversationResponse response = conversationService.getOrCreatePrivateConversation(friendId);
        return ResponseEntity.ok(ApiResponse.success(response, "Private conversation resolved successfully"));
    }

    @GetMapping
    @Operation(summary = "Get all conversations of the currently logged-in user")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getUserConversations() {
        List<ConversationResponse> response = conversationService.getUserConversations();
        return ResponseEntity.ok(ApiResponse.success(response, "User conversations retrieved successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get conversation details by ID")
    public ResponseEntity<ApiResponse<ConversationResponse>> getConversationById(@PathVariable("id") String id) {
        ConversationResponse response = conversationService.getConversationById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Conversation retrieved successfully"));
    }

    @PutMapping("/{id}/self-destruct")
    @Operation(summary = "Update self destruct message duration for a conversation")
    public ResponseEntity<ApiResponse<ConversationResponse>> updateSelfDestruct(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateSelfDestructRequest request
    ) {
        ConversationResponse response = conversationService.updateSelfDestruct(id, request.getSelfDestructSeconds());
        return ResponseEntity.ok(ApiResponse.success(response, "Self destruct setting updated successfully"));
    }

    @PutMapping("/{id}/pin")
    @Operation(summary = "Pin a conversation for the current user")
    public ResponseEntity<ApiResponse<ConversationResponse>> pinConversation(@PathVariable("id") String id) {
        ConversationResponse response = conversationService.updatePinned(id, true);
        return ResponseEntity.ok(ApiResponse.success(response, "Conversation pinned successfully"));
    }

    @DeleteMapping("/{id}/pin")
    @Operation(summary = "Unpin a conversation for the current user")
    public ResponseEntity<ApiResponse<ConversationResponse>> unpinConversation(@PathVariable("id") String id) {
        ConversationResponse response = conversationService.updatePinned(id, false);
        return ResponseEntity.ok(ApiResponse.success(response, "Conversation unpinned successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete or hide a conversation for the current user")
    public ResponseEntity<ApiResponse<Void>> deleteConversationForMe(@PathVariable("id") String id) {
        conversationService.deleteForCurrentUser(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Conversation deleted successfully"));
    }

    @GetMapping("/search")
    @Operation(summary = "Search conversations by name or username (partial match)")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> searchConversations(@RequestParam("query") String query) {
        List<ConversationResponse> response = conversationService.searchConversations(query);
        return ResponseEntity.ok(ApiResponse.success(response, "Conversations retrieved successfully"));
    }

    @PostMapping("/{id}/summary")
    @Operation(summary = "Summarize the latest messages of a conversation via n8n webhook")
    public ResponseEntity<ApiResponse<ConversationSummaryResponse>> summarizeConversation(@PathVariable("id") String id) {
        ConversationSummaryResponse response = conversationSummaryService.summarize(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Conversation summary generated successfully"));
    }
}
