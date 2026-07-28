package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.ChannelTaskResponse;
import iuh.fit.se.nextalk_be.service.ChannelTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class MyTaskController {

    private final ChannelTaskService channelTaskService;

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<ChannelTaskResponse>>> getMyTasks(
            @RequestParam(defaultValue = "false") boolean archived
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                channelTaskService.getMyTasks(archived),
                "My tasks retrieved successfully"
        ));
    }
}
