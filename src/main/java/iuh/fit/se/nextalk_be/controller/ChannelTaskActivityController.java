package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.TaskActivityResponse;
import iuh.fit.se.nextalk_be.service.ChannelTaskActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/channels/{channelId}/task-activities")
@RequiredArgsConstructor
public class ChannelTaskActivityController {

    private final ChannelTaskActivityService taskActivityService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskActivityResponse>>> getActivities(
            @PathVariable String groupId,
            @PathVariable String channelId
    ) {
        List<TaskActivityResponse> activities = taskActivityService.getActivities(groupId, channelId);
        return ResponseEntity.ok(ApiResponse.success(activities));
    }

    @PostMapping("/read")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @PathVariable String groupId,
            @PathVariable String channelId
    ) {
        taskActivityService.markAllAsRead(groupId, channelId);
        return ResponseEntity.ok(ApiResponse.success(null, "Marked all task activities as read"));
    }
}
