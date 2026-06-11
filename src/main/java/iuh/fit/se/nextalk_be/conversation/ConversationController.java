package iuh.fit.se.nextalk_be.conversation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.se.nextalk_be.common.ApiResponse;
import iuh.fit.se.nextalk_be.conversation.dto.ConversationResponse;
import iuh.fit.se.nextalk_be.summary.ConversationSummaryService;
import iuh.fit.se.nextalk_be.summary.dto.ConversationSummaryResponse;
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
