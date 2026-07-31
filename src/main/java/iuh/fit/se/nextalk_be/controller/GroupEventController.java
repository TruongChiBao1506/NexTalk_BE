package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.request.*;
import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.GroupEventResponse;
import iuh.fit.se.nextalk_be.service.GroupEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups/{groupId}/events")
@RequiredArgsConstructor
public class GroupEventController {
    private final GroupEventService eventService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GroupEventResponse>>> getUpcoming(@PathVariable String groupId) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getUpcoming(groupId), "Events retrieved successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GroupEventResponse>> create(
            @PathVariable String groupId, @Valid @RequestBody CreateGroupEventRequest request) {
        return ResponseEntity.ok(ApiResponse.success(eventService.create(groupId, request), "Event created successfully"));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<ApiResponse<GroupEventResponse>> update(
            @PathVariable String groupId,
            @PathVariable String eventId,
            @Valid @RequestBody UpdateGroupEventRequest request) {
        return ResponseEntity.ok(ApiResponse.success(eventService.update(groupId, eventId, request), "Event updated successfully"));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<ApiResponse<GroupEventResponse>> cancel(
            @PathVariable String groupId, @PathVariable String eventId) {
        return ResponseEntity.ok(ApiResponse.success(eventService.cancel(groupId, eventId), "Event cancelled successfully"));
    }

    @PutMapping("/{eventId}/rsvp")
    public ResponseEntity<ApiResponse<GroupEventResponse>> rsvp(
            @PathVariable String groupId,
            @PathVariable String eventId,
            @Valid @RequestBody UpdateGroupEventRsvpRequest request) {
        return ResponseEntity.ok(ApiResponse.success(eventService.rsvp(groupId, eventId, request), "RSVP updated successfully"));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> updateSettings(
            @PathVariable String groupId,
            @RequestBody UpdateGroupEventSettingsRequest request) {
        boolean enabled = eventService.updateSettings(groupId, request);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("membersCanCreateEvents", enabled), "Event settings updated successfully"));
    }
}
