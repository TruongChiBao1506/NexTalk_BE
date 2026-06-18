package iuh.fit.se.nextalk_be.message;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.se.nextalk_be.common.ApiResponse;
import iuh.fit.se.nextalk_be.message.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Message Management", description = "APIs for sending and retrieving chat messages")
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/api/messages")
    @Operation(summary = "Send a new message to a conversation (REST API)")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(@Valid @RequestBody MessageRequest request) {
        MessageResponse response = messageService.sendMessage(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Message sent successfully"));
    }

    @GetMapping("/api/messages/{conversationId}")
    @Operation(summary = "Get paginated messages of a conversation")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getConversationMessages(
            @PathVariable("conversationId") String conversationId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MessageResponse> responsePage = messageService.getConversationMessages(conversationId, pageable);
        return ResponseEntity.ok(ApiResponse.success(responsePage.getContent(), "Messages retrieved successfully"));
    }

    @PostMapping("/api/messages/status/delivered")
    @Operation(summary = "Mark conversation messages as delivered for current user")
    public ResponseEntity<ApiResponse<Void>> markConversationMessagesAsDelivered(
            @Valid @RequestBody MessageStatusUpdateRequest request, Principal principal
    ) {
        if (principal != null) {
            messageService.markConversationMessagesAsDelivered(request.getConversationId(), principal.getName());
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Messages marked as delivered"));
    }

    @PostMapping("/api/messages/status/seen")
    @Operation(summary = "Mark conversation messages as seen for current user")
    public ResponseEntity<ApiResponse<Void>> markConversationMessagesAsSeen(
            @Valid @RequestBody MessageStatusUpdateRequest request, Principal principal
    ) {
        if (principal != null) {
            messageService.markConversationMessagesAsSeen(request.getConversationId(), principal.getName());
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Messages marked as seen"));
    }

    @PutMapping("/api/messages/{id}")
    @Operation(summary = "Edit a message")
    public ResponseEntity<ApiResponse<MessageResponse>> editMessage(
            @PathVariable("id") String id,
            @Valid @RequestBody EditMessageRequest request
    ) {
        MessageResponse response = messageService.editMessage(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Message edited successfully"));
    }

    @PostMapping("/api/messages/{id}/recall")
    @Operation(summary = "Recall a message for everyone")
    public ResponseEntity<ApiResponse<MessageResponse>> recallMessage(@PathVariable("id") String id) {
        MessageResponse response = messageService.recallMessage(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Message recalled successfully"));
    }

    @DeleteMapping("/api/messages/{id}")
    @Operation(summary = "Delete a message for current user")
    public ResponseEntity<ApiResponse<Void>> deleteMessageForMe(@PathVariable("id") String id) {
        messageService.deleteMessageForMe(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Message deleted for me successfully"));
    }

    @PostMapping("/api/messages/{id}/pin")
    @Operation(summary = "Pin a message in conversation")
    public ResponseEntity<ApiResponse<MessageResponse>> pinMessage(@PathVariable("id") String id) {
        MessageResponse response = messageService.pinMessage(id, true);
        return ResponseEntity.ok(ApiResponse.success(response, "Message pinned successfully"));
    }

    @DeleteMapping("/api/messages/{id}/pin")
    @Operation(summary = "Unpin a message in conversation")
    public ResponseEntity<ApiResponse<MessageResponse>> unpinMessage(@PathVariable("id") String id) {
        MessageResponse response = messageService.pinMessage(id, false);
        return ResponseEntity.ok(ApiResponse.success(response, "Message unpinned successfully"));
    }

    @GetMapping("/api/conversations/{conversationId}/pinned")
    @Operation(summary = "Get all pinned messages in a conversation")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getPinnedMessages(@PathVariable("conversationId") String conversationId) {
        List<MessageResponse> response = messageService.getPinnedMessages(conversationId);
        return ResponseEntity.ok(ApiResponse.success(response, "Pinned messages retrieved successfully"));
    }

    @PostMapping("/api/messages/{id}/react")
    @Operation(summary = "Add/remove reaction on a message")
    public ResponseEntity<ApiResponse<MessageResponse>> reactToMessage(
            @PathVariable("id") String id,
            @Valid @RequestBody ReactMessageRequest request
    ) {
        MessageResponse response = messageService.reactToMessage(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Reaction updated successfully"));
    }

    @PostMapping("/api/messages/{id}/share")
    @Operation(summary = "Share a message to one or more conversations")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> shareMessage(
            @PathVariable("id") String id,
            @Valid @RequestBody ShareMessageRequest request
    ) {
        List<MessageResponse> response = messageService.shareMessage(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Message shared successfully"));
    }

    @PostMapping("/api/messages/polls")
    @Operation(summary = "Create a poll message in a group conversation")
    public ResponseEntity<ApiResponse<MessageResponse>> createPoll(@Valid @RequestBody CreatePollRequest request) {
        MessageResponse response = messageService.createPoll(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Poll created successfully"));
    }

    @PostMapping("/api/messages/{id}/poll/vote")
    @Operation(summary = "Vote or unvote an option in a poll")
    public ResponseEntity<ApiResponse<MessageResponse>> votePoll(
            @PathVariable("id") String id,
            @Valid @RequestBody PollVoteRequest request
    ) {
        MessageResponse response = messageService.votePoll(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Poll vote updated successfully"));
    }

    @PostMapping("/api/messages/{id}/poll/options")
    @Operation(summary = "Add a new option to a poll")
    public ResponseEntity<ApiResponse<MessageResponse>> addPollOption(
            @PathVariable("id") String id,
            @Valid @RequestBody AddPollOptionRequest request
    ) {
        MessageResponse response = messageService.addPollOption(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Poll option added successfully"));
    }

    @PostMapping("/api/messages/{id}/poll/lock")
    @Operation(summary = "Lock a poll")
    public ResponseEntity<ApiResponse<MessageResponse>> lockPoll(@PathVariable("id") String id) {
        MessageResponse response = messageService.lockPoll(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Poll locked successfully"));
    }

    @DeleteMapping("/api/messages/{id}/poll")
    @Operation(summary = "Delete a poll")
    public ResponseEntity<ApiResponse<MessageResponse>> deletePoll(@PathVariable("id") String id) {
        MessageResponse response = messageService.deletePoll(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Poll deleted successfully"));
    }

    @GetMapping("/api/messages/search")
    @Operation(summary = "Search messages by content (partial match)")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> searchMessages(
            @RequestParam("query") String query,
            @RequestParam(value = "conversationId", required = false) String conversationId
    ) {
        List<MessageResponse> response = messageService.searchMessages(query, conversationId);
        return ResponseEntity.ok(ApiResponse.success(response, "Messages search completed successfully"));
    }

    @MessageMapping("/chat.send")
    @Operation(summary = "Send a message via STOMP WebSocket")
    public void sendWebSocketMessage(@Valid @Payload MessageRequest request, Principal principal) {
        if (principal != null) {
            messageService.sendMessage(request, principal.getName());
        }
    }

    @MessageMapping("/chat.typing")
    @Operation(summary = "Broadcast typing indicator via STOMP WebSocket")
    public void sendTypingIndicator(@Valid @Payload TypingIndicatorRequest request, Principal principal) {
        if (principal != null) {
            messageService.broadcastTypingIndicator(request, principal.getName());
        }
    }

    @MessageMapping("/chat.delivered")
    @Operation(summary = "Mark conversation messages as delivered via WebSocket")
    public void markAsDeliveredWebSocket(@Valid @Payload MessageStatusUpdateRequest request, Principal principal) {
        if (principal != null) {
            messageService.markConversationMessagesAsDelivered(request.getConversationId(), principal.getName());
        }
    }

    @MessageMapping("/chat.seen")
    @Operation(summary = "Mark conversation messages as seen via WebSocket")
    public void markAsSeenWebSocket(@Valid @Payload MessageStatusUpdateRequest request, Principal principal) {
        if (principal != null) {
            messageService.markConversationMessagesAsSeen(request.getConversationId(), principal.getName());
        }
    }
}
