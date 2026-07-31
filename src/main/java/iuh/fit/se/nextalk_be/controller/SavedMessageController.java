package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.SavedMessageResponse;
import iuh.fit.se.nextalk_be.service.SavedMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/saved-messages")
@RequiredArgsConstructor
public class SavedMessageController {
    private final SavedMessageService savedMessageService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SavedMessageResponse>>> getMine(
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success(savedMessageService.getMine(limit), "Saved messages retrieved"));
    }

    @PostMapping("/{messageId}")
    public ResponseEntity<ApiResponse<SavedMessageResponse>> save(@PathVariable String messageId) {
        return ResponseEntity.ok(ApiResponse.success(savedMessageService.save(messageId), "Message saved"));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable String messageId) {
        savedMessageService.remove(messageId);
        return ResponseEntity.ok(ApiResponse.success(null, "Message removed from saved items"));
    }
}
