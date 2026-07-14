package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.request.TaskRequest;
import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.TaskResponse;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @AuthenticationPrincipal User userDetails,
            @RequestBody TaskRequest request
    ) {
        TaskResponse response = taskService.createTask(userDetails.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @AuthenticationPrincipal User userDetails,
            @PathVariable String taskId,
            @RequestBody TaskRequest request
    ) {
        TaskResponse response = taskService.updateTask(userDetails.getId(), taskId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(
            @AuthenticationPrincipal User userDetails,
            @PathVariable String taskId
    ) {
        taskService.deleteTask(userDetails.getId(), taskId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getTasksByConversation(
            @PathVariable String conversationId
    ) {
        List<TaskResponse> responses = taskService.getTasksByConversation(conversationId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
