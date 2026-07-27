package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.request.TaskAssistantRequest;
import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.TaskAssistantResponse;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.service.TaskAssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/task-assistant")
@RequiredArgsConstructor
public class TaskAssistantController {
    private final TaskAssistantService taskAssistantService;
    private final RateLimitService rateLimitService;

    @Value("${app.rate-limit.task-assistant.limit:30}")
    private int requestLimit;

    @Value("${app.rate-limit.task-assistant.window-seconds:86400}")
    private long requestWindowSeconds;

    @PostMapping("/ask")
    public ResponseEntity<ApiResponse<TaskAssistantResponse>> ask(
            @Valid @RequestBody TaskAssistantRequest request
    ) {
        rateLimitService.check(
                "task-assistant:ask:v1",
                rateLimitService.currentUserIdentity(),
                requestLimit,
                Duration.ofSeconds(requestWindowSeconds),
                "Bạn đã dùng hết lượt Trợ lý tác vụ hiện tại. Vui lòng thử lại sau."
        );
        return ResponseEntity.ok(ApiResponse.success(
                taskAssistantService.ask(request),
                "Task assistant request processed"
        ));
    }

    @PostMapping("/{confirmationId}/confirm")
    public ResponseEntity<ApiResponse<TaskAssistantResponse>> confirm(
            @PathVariable String confirmationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                taskAssistantService.confirm(confirmationId),
                "Task assistant action confirmed"
        ));
    }

    @PostMapping("/{confirmationId}/reject")
    public ResponseEntity<ApiResponse<TaskAssistantResponse>> reject(
            @PathVariable String confirmationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                taskAssistantService.reject(confirmationId),
                "Task assistant action rejected"
        ));
    }
}
