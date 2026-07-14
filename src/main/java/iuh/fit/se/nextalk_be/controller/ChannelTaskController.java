package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.request.CreateChannelTaskRequest;
import iuh.fit.se.nextalk_be.dto.request.UpdateChannelTaskRequest;
import iuh.fit.se.nextalk_be.dto.request.UpdateChannelTaskStatusRequest;
import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.ChannelTaskResponse;
import iuh.fit.se.nextalk_be.service.ChannelTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/channels/{channelId}/tasks")
@RequiredArgsConstructor
public class ChannelTaskController {

    private final ChannelTaskService channelTaskService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChannelTaskResponse>>> getTasks(
            @PathVariable String groupId,
            @PathVariable String channelId) {
        return ResponseEntity.ok(ApiResponse.success(
                channelTaskService.getTasks(groupId, channelId),
                "Tasks retrieved successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChannelTaskResponse>> createTask(
            @PathVariable String groupId,
            @PathVariable String channelId,
            @Valid @RequestBody CreateChannelTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                channelTaskService.createTask(groupId, channelId, request),
                "Task created successfully"));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<ApiResponse<ChannelTaskResponse>> updateTask(
            @PathVariable String groupId,
            @PathVariable String channelId,
            @PathVariable String taskId,
            @Valid @RequestBody UpdateChannelTaskRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                channelTaskService.updateTask(groupId, channelId, taskId, request),
                "Task updated successfully"));
    }

    @PatchMapping("/{taskId}/status")
    public ResponseEntity<ApiResponse<ChannelTaskResponse>> updateStatus(
            @PathVariable String groupId,
            @PathVariable String channelId,
            @PathVariable String taskId,
            @Valid @RequestBody UpdateChannelTaskStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                channelTaskService.updateStatus(groupId, channelId, taskId, request.getStatus()),
                "Task status updated successfully"));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(
            @PathVariable String groupId,
            @PathVariable String channelId,
            @PathVariable String taskId) {
        channelTaskService.deleteTask(groupId, channelId, taskId);
        return ResponseEntity.ok(ApiResponse.success(null, "Task deleted successfully"));
    }
}
