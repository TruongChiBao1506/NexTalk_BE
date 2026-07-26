package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.request.ScheduleMessageRequest;
import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.ScheduledMessageResponse;
import iuh.fit.se.nextalk_be.service.ScheduledMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages/scheduled")
@RequiredArgsConstructor
public class ScheduledMessageController {
    private final ScheduledMessageService scheduledMessageService;

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduledMessageResponse>> schedule(
            @Valid @RequestBody ScheduleMessageRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                scheduledMessageService.schedule(request),
                "Message scheduled successfully"
        ));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ScheduledMessageResponse>>> getPending() {
        return ResponseEntity.ok(ApiResponse.success(
                scheduledMessageService.getPending(),
                "Scheduled messages retrieved successfully"
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<ScheduledMessageResponse>> cancel(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(
                scheduledMessageService.cancel(id),
                "Scheduled message cancelled successfully"
        ));
    }
}
