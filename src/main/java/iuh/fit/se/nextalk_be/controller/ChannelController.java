package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.request.CreateChannelRequest;
import iuh.fit.se.nextalk_be.dto.request.UpdateChannelRequest;
import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.ChannelResponse;
import iuh.fit.se.nextalk_be.entity.Channel;
import iuh.fit.se.nextalk_be.service.ChannelService;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @PostMapping
    public ResponseEntity<ApiResponse<ChannelResponse>> createChannel(
            @PathVariable String groupId,
            @Valid @RequestBody CreateChannelRequest request) {
        ChannelResponse response = channelService.createChannel(groupId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Channel created successfully"));
    }

    @PutMapping("/{channelId}")
    public ResponseEntity<ApiResponse<ChannelResponse>> updateChannel(
            @PathVariable String groupId,
            @PathVariable String channelId,
            @Valid @RequestBody UpdateChannelRequest request) {
        ChannelResponse response = channelService.updateChannel(groupId, channelId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Channel updated successfully"));
    }

    @DeleteMapping("/{channelId}")
    public ResponseEntity<ApiResponse<Void>> deleteChannel(
            @PathVariable String groupId,
            @PathVariable String channelId) {
        channelService.deleteChannel(groupId, channelId);
        return ResponseEntity.ok(ApiResponse.success(null, "Channel deleted successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChannelResponse>>> getChannelsByGroupId(
            @PathVariable String groupId) {
        List<ChannelResponse> response = channelService.getChannelsByGroupId(groupId);
        return ResponseEntity.ok(ApiResponse.success(response, "Channels retrieved successfully"));
    }
}
