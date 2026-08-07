package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.request.UpdateSelfDestructRequest;
import iuh.fit.se.nextalk_be.dto.request.UpdateThemeRequest;
import iuh.fit.se.nextalk_be.dto.request.UpdateNicknameRequest;
import iuh.fit.se.nextalk_be.dto.request.UpdateConversationNotificationRequest;
import iuh.fit.se.nextalk_be.dto.request.ReplySuggestionRequest;
import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.BirthdayContextResponse;
import iuh.fit.se.nextalk_be.dto.response.ConversationResponse;
import iuh.fit.se.nextalk_be.dto.response.ConversationSummaryResponse;
import iuh.fit.se.nextalk_be.dto.response.ConversationWithPreviewsResponse;
import iuh.fit.se.nextalk_be.dto.response.ReplySuggestionsResponse;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.service.ConversationService;
import iuh.fit.se.nextalk_be.service.ConversationSummaryService;
import iuh.fit.se.nextalk_be.service.ConversationAssistService;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversation Management", description = "APIs for creating and listing chat conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationSummaryService conversationSummaryService;
    private final RateLimitService rateLimitService;
    private final ConversationAssistService conversationAssistService;


    @PostMapping("/private/{friendId}")
    @Operation(summary = "Get or create a private conversation with a friend")
    public ResponseEntity<ApiResponse<ConversationResponse>> getOrCreatePrivateConversation(@PathVariable("friendId") String friendId) {
        ConversationResponse response = conversationService.getOrCreatePrivateConversation(friendId);
        return ResponseEntity.ok(ApiResponse.success(response, "Private conversation resolved successfully"));
    }

    @PostMapping("/cloud")
    @Operation(summary = "Get or create a cloud conversation for the current user")
    public ResponseEntity<ApiResponse<ConversationResponse>> getOrCreateCloudConversation() {
        ConversationResponse response = conversationService.getOrCreateCloudConversation();
        return ResponseEntity.ok(ApiResponse.success(response, "Cloud conversation resolved successfully"));
    }

    @GetMapping
    @Operation(summary = "Get all conversations of the currently logged-in user")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getUserConversations() {
        List<ConversationResponse> response = conversationService.getUserConversations();
        return ResponseEntity.ok(ApiResponse.success(response, "User conversations retrieved successfully"));
    }

    @GetMapping("/with-previews")
    @Operation(summary = "Get all conversations along with latest messages and unread counts in a single request")
    public ResponseEntity<ApiResponse<ConversationWithPreviewsResponse>> getUserConversationsWithPreviews() {
        ConversationWithPreviewsResponse response = conversationService.getUserConversationsWithPreviews();
        return ResponseEntity.ok(ApiResponse.success(response, "User conversations with previews retrieved successfully"));
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
        if (query != null && query.trim().matches("\\d{4}")) {
            rateLimitService.check("chat-pin:unlock", rateLimitService.currentUserIdentity(), 5, Duration.ofMinutes(1));
        }
        rateLimitService.check("conversation:search", rateLimitService.currentUserIdentity(), 60, Duration.ofMinutes(1));
        List<ConversationResponse> response = conversationService.searchConversations(query);
        return ResponseEntity.ok(ApiResponse.success(response, "Conversations retrieved successfully"));
    }

    @PutMapping("/{id}/hidden")
    @Operation(summary = "Hide/unhide a conversation for the current user")
    public ResponseEntity<ApiResponse<ConversationResponse>> updateHidden(
            @PathVariable("id") String id,
            @RequestParam("hidden") boolean hidden
    ) {
        ConversationResponse response = conversationService.updateHidden(id, hidden);
        return ResponseEntity.ok(ApiResponse.success(response, hidden ? "Conversation hidden successfully" : "Conversation unhidden successfully"));
    }

    @PutMapping("/{id}/muted")
    @Operation(summary = "Mute/unmute notifications for the current user")
    public ResponseEntity<ApiResponse<ConversationResponse>> updateMuted(
            @PathVariable("id") String id,
            @RequestParam("muted") boolean muted
    ) {
        ConversationResponse response = conversationService.updateMuted(id, muted);
        return ResponseEntity.ok(ApiResponse.success(response, muted ? "Conversation muted" : "Conversation unmuted"));
    }

    @PutMapping("/{id}/notification-settings")
    @Operation(summary = "Update detailed notification settings for the current user")
    public ResponseEntity<ApiResponse<ConversationResponse>> updateNotificationSettings(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateConversationNotificationRequest request
    ) {
        ConversationResponse response = conversationService.updateNotificationSettings(
                id,
                request.getMode(),
                request.getMutedUntil()
        );
        return ResponseEntity.ok(ApiResponse.success(response, "Notification settings updated"));
    }

    @PostMapping("/{id}/summary")
    @Operation(summary = "Summarize the latest messages of a conversation via n8n webhook")
    public ResponseEntity<ApiResponse<ConversationSummaryResponse>> summarizeConversation(@PathVariable("id") String id) {
        ConversationSummaryResponse response = conversationSummaryService.summarize(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Conversation summary generated successfully"));
    }

    @PostMapping("/{id}/reply-suggestions")
    @Operation(summary = "Generate three short reply suggestions from recent conversation context")
    public ResponseEntity<ApiResponse<ReplySuggestionsResponse>> suggestReplies(
            @PathVariable("id") String id,
            @RequestBody(required = false) ReplySuggestionRequest request
    ) {
        ReplySuggestionsResponse response = conversationAssistService.suggestReplies(
                id,
                request == null ? null : request.getLastMessageId()
        );
        return ResponseEntity.ok(ApiResponse.success(response, "Reply suggestions generated"));
    }

    @GetMapping("/{id}/birthday-context")
    @Operation(summary = "Get a privacy-filtered birthday reminder and template wishes for a private chat")
    public ResponseEntity<ApiResponse<BirthdayContextResponse>> getBirthdayContext(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationAssistService.getBirthdayContext(id),
                "Birthday context retrieved"
        ));
    }

    @PostMapping("/{id}/birthday-wishes/personalize")
    @Operation(summary = "Generate three personalized birthday wishes")
    public ResponseEntity<ApiResponse<ReplySuggestionsResponse>> personalizeBirthdayWishes(
            @PathVariable("id") String id,
            @RequestBody(required = false) ReplySuggestionRequest request
    ) {
        ReplySuggestionsResponse response = conversationAssistService.personalizeBirthdayWishes(
                id,
                request == null ? null : request.getLastMessageId()
        );
        return ResponseEntity.ok(ApiResponse.success(response, "Birthday wishes personalized"));
    }

    @PutMapping("/{id}/theme")
    @Operation(summary = "Update conversation theme color and wallpaper")
    public ResponseEntity<ApiResponse<ConversationResponse>> updateTheme(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateThemeRequest request
    ) {
        ConversationResponse response = conversationService.updateTheme(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Conversation theme updated successfully"));
    }

    @PutMapping("/{id}/nicknames/{userId}")
    @Operation(summary = "Set or remove a member nickname inside a conversation")
    public ResponseEntity<ApiResponse<ConversationResponse>> updateNickname(
            @PathVariable("id") String id,
            @PathVariable("userId") String userId,
            @Valid @RequestBody UpdateNicknameRequest request) {
        ConversationResponse response = conversationService.updateNickname(id, userId, request.getNickname());
        return ResponseEntity.ok(ApiResponse.success(response, "Nickname updated successfully"));
    }

    @PutMapping("/{id}/word-effects")
    @Operation(summary = "Update shared word effects for a conversation")
    public ResponseEntity<ApiResponse<ConversationResponse>> updateWordEffects(
            @PathVariable("id") String id,
            @Valid @RequestBody iuh.fit.se.nextalk_be.dto.request.UpdateWordEffectsRequest request
    ) {
        ConversationResponse response = conversationService.updateWordEffects(id, request.getWordEffects());
        return ResponseEntity.ok(ApiResponse.success(response, "Word effects updated successfully"));
    }
}
