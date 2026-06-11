package iuh.fit.se.nextalk_be.chatrequest;

import iuh.fit.se.nextalk_be.chatrequest.dto.ChatRequestResponse;
import iuh.fit.se.nextalk_be.chatrequest.dto.CreateChatRequest;
import iuh.fit.se.nextalk_be.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat-requests")
@RequiredArgsConstructor
public class ChatRequestController {

    private final ChatRequestService chatRequestService;

    @PostMapping
    public ResponseEntity<ApiResponse<ChatRequestResponse>> create(@Valid @RequestBody CreateChatRequest request) {
        return ResponseEntity.ok(ApiResponse.success(chatRequestService.create(request), "Chat request sent successfully"));
    }

    @GetMapping("/incoming")
    public ResponseEntity<ApiResponse<List<ChatRequestResponse>>> incoming() {
        return ResponseEntity.ok(ApiResponse.success(chatRequestService.getIncomingPending(), "Incoming chat requests retrieved"));
    }

    @GetMapping("/outgoing")
    public ResponseEntity<ApiResponse<List<ChatRequestResponse>>> outgoing() {
        return ResponseEntity.ok(ApiResponse.success(chatRequestService.getOutgoingPending(), "Outgoing chat requests retrieved"));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<ChatRequestResponse>> accept(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(chatRequestService.accept(id), "Chat request accepted"));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<ChatRequestResponse>> reject(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(chatRequestService.reject(id), "Chat request rejected"));
    }
}
