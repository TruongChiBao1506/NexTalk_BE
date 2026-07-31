package iuh.fit.se.nextalk_be.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.nextalk_be.dto.request.*;
import iuh.fit.se.nextalk_be.dto.response.GroupEventParticipantResponse;
import iuh.fit.se.nextalk_be.dto.response.GroupEventResponse;
import iuh.fit.se.nextalk_be.entity.*;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GroupEventService {
    private static final Set<Integer> ALLOWED_REMINDERS = Set.of(0, 15, 60, 1440);

    private final GroupEventRepository eventRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final ChannelRepository channelRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final MessageService messageService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Transactional
    public GroupEventResponse create(String groupId, CreateGroupEventRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = requireGroupAndMembership(groupId, currentUser);
        GroupMember membership = memberRepository.findByGroupIdAndUserId(groupId, currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this group"));
        if (!group.isMembersCanCreateEvents() && !canModerate(membership.getRole())) {
            throw new AccessDeniedException("Only group managers can create events");
        }

        Channel channel = channelRepository.findByConversationId(request.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found"));
        if (channel.getGroup() == null || !groupId.equals(channel.getGroup().getId())
                || channel.getConversation() == null
                || channel.getConversation().getMembers().stream().noneMatch(u -> currentUser.getId().equals(u.getId()))) {
            throw new AccessDeniedException("Conversation does not belong to this group");
        }
        if (channel.getType() != ChannelType.TEXT) {
            throw new BadRequestException("Events can only be posted to text channels");
        }
        validate(request.getStartsAt(), request.getEndsAt(), request.getReminderMinutes(), request.getMeetingUrl());

        GroupEvent event = GroupEvent.builder()
                .group(group)
                .conversation(channel.getConversation())
                .creator(currentUser)
                .title(request.getTitle().trim())
                .description(clean(request.getDescription()))
                .location(clean(request.getLocation()))
                .meetingUrl(clean(request.getMeetingUrl()))
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .reminderMinutes(request.getReminderMinutes())
                .remindAt(remindAt(request.getStartsAt(), request.getReminderMinutes()))
                .reminderSent(request.getReminderMinutes() == 0)
                .responses(new HashMap<>(Map.of(currentUser.getId(), GroupEventRsvpStatus.ATTENDING)))
                .build();
        event = eventRepository.save(event);

        GroupEventResponse response = map(event, currentUser);
        var message = messageService.createAndBroadcastSystemMessage(
                channel.getConversation(),
                currentUser,
                currentUser.getUsername() + " đã tạo sự kiện: " + event.getTitle(),
                metadata(response));
        event.setMessageId(message.getId());
        event = eventRepository.save(event);
        return map(event, currentUser);
    }

    public List<GroupEventResponse> getUpcoming(String groupId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        requireGroupAndMembership(groupId, currentUser);
        return eventRepository
                .findAllByGroupIdAndStatusAndStartsAtGreaterThanEqualOrderByStartsAtAsc(
                        groupId, GroupEventStatus.SCHEDULED, LocalDateTime.now())
                .stream().map(event -> map(event, currentUser)).toList();
    }

    @Transactional
    public GroupEventResponse update(String groupId, String eventId, UpdateGroupEventRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        requireGroupAndMembership(groupId, currentUser);
        GroupEvent event = requireEvent(groupId, eventId);
        requireManagePermission(groupId, event, currentUser);
        if (event.getStatus() == GroupEventStatus.CANCELLED) {
            throw new BadRequestException("Cancelled events cannot be edited");
        }
        validate(request.getStartsAt(), request.getEndsAt(), request.getReminderMinutes(), request.getMeetingUrl());
        event.setTitle(request.getTitle().trim());
        event.setDescription(clean(request.getDescription()));
        event.setLocation(clean(request.getLocation()));
        event.setMeetingUrl(clean(request.getMeetingUrl()));
        event.setStartsAt(request.getStartsAt());
        event.setEndsAt(request.getEndsAt());
        event.setReminderMinutes(request.getReminderMinutes());
        event.setRemindAt(remindAt(request.getStartsAt(), request.getReminderMinutes()));
        event.setReminderSent(request.getReminderMinutes() == 0);
        event = eventRepository.save(event);
        broadcastUpdate(event, currentUser, event.getCreator().getUsername() + " đã cập nhật sự kiện: " + event.getTitle());
        return map(event, currentUser);
    }

    @Transactional
    public GroupEventResponse cancel(String groupId, String eventId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        requireGroupAndMembership(groupId, currentUser);
        GroupEvent event = requireEvent(groupId, eventId);
        requireManagePermission(groupId, event, currentUser);
        event.setStatus(GroupEventStatus.CANCELLED);
        event.setReminderSent(true);
        event = eventRepository.save(event);
        broadcastUpdate(event, currentUser, currentUser.getUsername() + " đã hủy sự kiện: " + event.getTitle());
        return map(event, currentUser);
    }

    @Transactional
    public GroupEventResponse rsvp(String groupId, String eventId, UpdateGroupEventRsvpRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        requireGroupAndMembership(groupId, currentUser);
        GroupEvent event = requireEvent(groupId, eventId);
        if (event.getStatus() == GroupEventStatus.CANCELLED) {
            throw new BadRequestException("Cannot RSVP to a cancelled event");
        }
        Map<String, GroupEventRsvpStatus> responses = new HashMap<>(
                event.getResponses() == null ? Map.of() : event.getResponses());
        responses.put(currentUser.getId(), request.getStatus());
        event.setResponses(responses);
        event = eventRepository.save(event);
        broadcastUpdate(event, currentUser, event.getCreator().getUsername() + " đã tạo sự kiện: " + event.getTitle());
        return map(event, currentUser);
    }

    @Transactional
    public boolean updateSettings(String groupId, UpdateGroupEventSettingsRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Group group = requireGroupAndMembership(groupId, currentUser);
        GroupMember membership = memberRepository.findByGroupIdAndUserId(groupId, currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this group"));
        if (!canModerate(membership.getRole())) {
            throw new AccessDeniedException("Only group managers can change event settings");
        }
        group.setMembersCanCreateEvents(request.isMembersCanCreateEvents());
        groupRepository.save(group);
        return group.isMembersCanCreateEvents();
    }

    @Scheduled(fixedDelay = 60_000)
    public void sendDueReminders() {
        List<GroupEvent> due = eventRepository.findAllByStatusAndReminderSentFalseAndRemindAtLessThanEqual(
                GroupEventStatus.SCHEDULED, LocalDateTime.now());
        for (GroupEvent event : due) {
            Set<String> recipients = new HashSet<>();
            if (event.getCreator() != null) recipients.add(event.getCreator().getId());
            if (event.getResponses() != null) {
                event.getResponses().forEach((userId, status) -> {
                    if (status == GroupEventRsvpStatus.ATTENDING || status == GroupEventRsvpStatus.MAYBE) {
                        recipients.add(userId);
                    }
                });
            }
            for (String userId : recipients) {
                userRepository.findById(userId).ifPresent(user -> notificationService.createAndSend(
                        user,
                        NotificationType.GROUP_EVENT_REMINDER,
                        "Sự kiện nhóm sắp bắt đầu",
                        event.getConversation().getId(),
                        event.getId()));
            }
            event.setReminderSent(true);
            eventRepository.save(event);
        }
    }

    private Group requireGroupAndMembership(String groupId, User user) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        if (!memberRepository.existsByGroupIdAndUserId(groupId, user.getId())) {
            throw new AccessDeniedException("You are not a member of this group");
        }
        return group;
    }

    private GroupEvent requireEvent(String groupId, String eventId) {
        GroupEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        if (event.getGroup() == null || !groupId.equals(event.getGroup().getId())) {
            throw new ResourceNotFoundException("Event not found");
        }
        return event;
    }

    private void requireManagePermission(String groupId, GroupEvent event, User user) {
        GroupMember membership = memberRepository.findByGroupIdAndUserId(groupId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this group"));
        if (!user.getId().equals(event.getCreator().getId()) && !canModerate(membership.getRole())) {
            throw new AccessDeniedException("You cannot manage this event");
        }
    }

    private boolean canModerate(GroupRole role) {
        return role == GroupRole.OWNER || role == GroupRole.LEADER
                || role == GroupRole.DEPUTY || role == GroupRole.ADMIN;
    }

    private void validate(LocalDateTime startsAt, LocalDateTime endsAt, int reminderMinutes, String meetingUrl) {
        if (endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new BadRequestException("Event end time must be after the start time");
        }
        if (!ALLOWED_REMINDERS.contains(reminderMinutes)) {
            throw new BadRequestException("Reminder must be 0, 15, 60, or 1440 minutes");
        }
        if (meetingUrl != null && !meetingUrl.isBlank()) {
            try {
                URI uri = URI.create(meetingUrl.trim());
                if ((!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null || uri.getHost().isBlank()) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Meeting link must be a valid HTTP or HTTPS URL");
            }
        }
    }

    private LocalDateTime remindAt(LocalDateTime startsAt, int reminderMinutes) {
        return reminderMinutes == 0 ? null : startsAt.minusMinutes(reminderMinutes);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void broadcastUpdate(GroupEvent event, User viewer, String content) {
        if (event.getMessageId() != null) {
            messageService.updateAndBroadcastSystemMessage(event.getMessageId(), content, metadata(map(event, viewer)));
        }
    }

    private Map<String, Object> metadata(GroupEventResponse response) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("systemType", "GROUP_EVENT");
        Map<String, Object> eventData = objectMapper.convertValue(
                response, new TypeReference<Map<String, Object>>() {});
        // A system message is broadcast to the whole conversation. Never embed a
        // viewer-specific RSVP value in that shared payload; clients derive it
        // from participants using their own authenticated user id.
        eventData.put("currentUserRsvp", null);
        metadata.put("event", eventData);
        return metadata;
    }

    private GroupEventResponse map(GroupEvent event, User viewer) {
        Map<String, GroupEventRsvpStatus> responses =
                event.getResponses() == null ? Map.of() : event.getResponses();
        Map<String, User> users = new HashMap<>();
        if (!responses.isEmpty()) {
            userRepository.findAllById(responses.keySet()).forEach(user -> users.put(user.getId(), user));
        }
        List<GroupEventParticipantResponse> participants = responses.entrySet().stream()
                .map(entry -> {
                    User user = users.get(entry.getKey());
                    return GroupEventParticipantResponse.builder()
                            .userId(entry.getKey())
                            .username(user != null ? user.getUsername() : "Thành viên")
                            .avatarUrl(user != null ? user.getAvatarUrl() : null)
                            .status(entry.getValue())
                            .build();
                })
                .sorted(Comparator.comparing(GroupEventParticipantResponse::getUsername, String.CASE_INSENSITIVE_ORDER))
                .toList();
        GroupMember membership = memberRepository.findByGroupIdAndUserId(event.getGroup().getId(), viewer.getId()).orElse(null);
        boolean canManage = viewer.getId().equals(event.getCreator().getId())
                || (membership != null && canModerate(membership.getRole()));
        return GroupEventResponse.builder()
                .id(event.getId())
                .groupId(event.getGroup().getId())
                .conversationId(event.getConversation().getId())
                .messageId(event.getMessageId())
                .title(event.getTitle())
                .description(event.getDescription())
                .location(event.getLocation())
                .meetingUrl(event.getMeetingUrl())
                .startsAt(event.getStartsAt())
                .endsAt(event.getEndsAt())
                .reminderMinutes(event.getReminderMinutes())
                .status(event.getStatus())
                .creatorId(event.getCreator().getId())
                .creatorUsername(event.getCreator().getUsername())
                .creatorAvatarUrl(event.getCreator().getAvatarUrl())
                .currentUserRsvp(responses.get(viewer.getId()))
                .attendingCount((int) responses.values().stream().filter(v -> v == GroupEventRsvpStatus.ATTENDING).count())
                .maybeCount((int) responses.values().stream().filter(v -> v == GroupEventRsvpStatus.MAYBE).count())
                .notAttendingCount((int) responses.values().stream().filter(v -> v == GroupEventRsvpStatus.NOT_ATTENDING).count())
                .participants(participants)
                .canManage(canManage)
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
