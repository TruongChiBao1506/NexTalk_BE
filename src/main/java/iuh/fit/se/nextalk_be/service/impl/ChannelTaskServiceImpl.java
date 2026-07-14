package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.request.CreateChannelTaskRequest;
import iuh.fit.se.nextalk_be.dto.request.UpdateChannelTaskRequest;
import iuh.fit.se.nextalk_be.dto.response.ChannelTaskAssigneeResponse;
import iuh.fit.se.nextalk_be.dto.response.ChannelTaskResponse;
import iuh.fit.se.nextalk_be.entity.*;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.ChannelTaskRepository;
import iuh.fit.se.nextalk_be.repository.GroupMemberRepository;
import iuh.fit.se.nextalk_be.repository.GroupRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.ChannelTaskService;
import iuh.fit.se.nextalk_be.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChannelTaskServiceImpl implements ChannelTaskService {

    private final ChannelTaskRepository channelTaskRepository;
    private final ChannelRepository channelRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    @Override
    public List<ChannelTaskResponse> getTasks(String groupId, String channelId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Channel channel = getAccessibleChannel(groupId, channelId, currentUser);

        return channelTaskRepository.findAllByChannelIdOrderByCreatedAtDesc(channel.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public ChannelTaskResponse createTask(String groupId, String channelId, CreateChannelTaskRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Channel channel = getAccessibleChannel(groupId, channelId, currentUser);
        Group group = channel.getGroup();

        ChannelTask task = ChannelTask.builder()
                .group(group)
                .channel(channel)
                .createdBy(currentUser)
                .title(normalizeTitle(request.getTitle()))
                .description(trimToNull(request.getDescription()))
                .status(request.getStatus() != null ? request.getStatus() : ChannelTaskStatus.TODO)
                .priority(request.getPriority() != null ? request.getPriority() : ChannelTaskPriority.MEDIUM)
                .dueAt(parseOptionalDateTime(request.getDueAt(), "Invalid due date"))
                .assignees(resolveAssignees(groupId, channel, request.getAssigneeIds()))
                .build();

        if (task.getStatus() == ChannelTaskStatus.DONE) {
            task.setCompletedAt(LocalDateTime.now());
        }

        return mapToResponse(channelTaskRepository.save(task));
    }

    @Override
    public ChannelTaskResponse updateTask(String groupId, String channelId, String taskId, UpdateChannelTaskRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Channel channel = getAccessibleChannel(groupId, channelId, currentUser);
        ChannelTask task = getTaskInChannel(taskId, channel);
        ensureCanModifyTask(groupId, task, currentUser);

        if (request.getTitle() != null) {
            task.setTitle(normalizeTitle(request.getTitle()));
        }
        if (request.getDescription() != null) {
            task.setDescription(trimToNull(request.getDescription()));
        }
        if (request.getPriority() != null) {
            task.setPriority(request.getPriority());
        }
        if (request.getDueAt() != null) {
            task.setDueAt(parseOptionalDateTime(request.getDueAt(), "Invalid due date"));
        }
        if (request.getAssigneeIds() != null) {
            task.setAssignees(resolveAssignees(groupId, channel, request.getAssigneeIds()));
        }
        if (request.getStatus() != null) {
            applyStatus(task, request.getStatus());
        }

        task.setUpdatedAt(LocalDateTime.now());
        return mapToResponse(channelTaskRepository.save(task));
    }

    @Override
    public ChannelTaskResponse updateStatus(String groupId, String channelId, String taskId, ChannelTaskStatus status) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Channel channel = getAccessibleChannel(groupId, channelId, currentUser);
        ChannelTask task = getTaskInChannel(taskId, channel);
        ensureCanModifyTask(groupId, task, currentUser);

        applyStatus(task, status);
        task.setUpdatedAt(LocalDateTime.now());
        return mapToResponse(channelTaskRepository.save(task));
    }

    @Override
    public void deleteTask(String groupId, String channelId, String taskId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Channel channel = getAccessibleChannel(groupId, channelId, currentUser);
        ChannelTask task = getTaskInChannel(taskId, channel);

        if (!isCreator(task, currentUser) && !isLeaderRole(getRole(channel.getGroup(), currentUser).orElse(null))) {
            throw new UnauthorizedException("Only task creators or group leaders can delete tasks");
        }

        channelTaskRepository.delete(task);
    }

    private Channel getAccessibleChannel(String groupId, String channelId, User user) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found"));

        if (channel.getGroup() == null || !groupId.equals(channel.getGroup().getId())) {
            throw new BadRequestException("Channel does not belong to this group");
        }

        boolean isOwner = group.getOwner().getId().equals(user.getId());
        boolean isMember = groupMemberRepository.existsByGroupIdAndUserId(groupId, user.getId());
        if (!isOwner && !isMember) {
            throw new UnauthorizedException("You are not a member of this group");
        }

        if (channel.isPrivate() && !isUserInConversation(channel.getConversation(), user.getId())) {
            throw new UnauthorizedException("You are not a member of this channel");
        }

        return channel;
    }

    private ChannelTask getTaskInChannel(String taskId, Channel channel) {
        ChannelTask task = channelTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        if (task.getChannel() == null || !channel.getId().equals(task.getChannel().getId())) {
            throw new BadRequestException("Task does not belong to this channel");
        }
        return task;
    }

    private Set<User> resolveAssignees(String groupId, Channel channel, Set<String> assigneeIds) {
        Set<User> assignees = new HashSet<>();
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return assignees;
        }

        Set<String> allowedUserIds = getAllowedUserIds(groupId, channel);
        for (String userId : assigneeIds) {
            if (!allowedUserIds.contains(userId)) {
                throw new BadRequestException("Assignee is not a member of this channel: " + userId);
            }
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
            assignees.add(user);
        }
        return assignees;
    }

    private Set<String> getAllowedUserIds(String groupId, Channel channel) {
        if (channel.isPrivate()) {
            Conversation conversation = channel.getConversation();
            if (conversation == null || conversation.getMembers() == null) {
                return Set.of();
            }
            return conversation.getMembers().stream().map(User::getId).collect(Collectors.toSet());
        }

        Set<String> allowedUserIds = groupMemberRepository.findAllByGroupId(groupId)
                .stream()
                .map(member -> member.getUser().getId())
                .collect(Collectors.toSet());
        allowedUserIds.add(channel.getGroup().getOwner().getId());
        return allowedUserIds;
    }

    private void ensureCanModifyTask(String groupId, ChannelTask task, User user) {
        if (isCreator(task, user) || isAssignee(task, user) || isLeaderRole(getRole(task.getGroup(), user).orElse(null))) {
            return;
        }
        throw new UnauthorizedException("Only task creators, assignees, or group leaders can update tasks");
    }

    private boolean isCreator(ChannelTask task, User user) {
        return task.getCreatedBy() != null && task.getCreatedBy().getId().equals(user.getId());
    }

    private boolean isAssignee(ChannelTask task, User user) {
        return task.getAssignees() != null
                && task.getAssignees().stream().anyMatch(assignee -> assignee.getId().equals(user.getId()));
    }

    private boolean isUserInConversation(Conversation conversation, String userId) {
        if (conversation == null || conversation.getMembers() == null) return false;
        return conversation.getMembers().stream().anyMatch(member -> member.getId().equals(userId));
    }

    private Optional<GroupRole> getRole(Group group, User user) {
        if (group.getOwner().getId().equals(user.getId())) {
            return Optional.of(GroupRole.OWNER);
        }
        return groupMemberRepository.findByGroupIdAndUserId(group.getId(), user.getId())
                .map(GroupMember::getRole);
    }

    private boolean isLeaderRole(GroupRole role) {
        return role != null && (role == GroupRole.OWNER || role == GroupRole.LEADER || role == GroupRole.ADMIN);
    }

    private void applyStatus(ChannelTask task, ChannelTaskStatus status) {
        task.setStatus(status);
        if (status == ChannelTaskStatus.DONE && task.getCompletedAt() == null) {
            task.setCompletedAt(LocalDateTime.now());
        } else if (status != ChannelTaskStatus.DONE) {
            task.setCompletedAt(null);
        }
    }

    private String normalizeTitle(String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isBlank()) {
            throw new BadRequestException("Task title is required");
        }
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private LocalDateTime parseOptionalDateTime(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Fall through.
        }

        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Fall through.
        }

        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            throw new BadRequestException(errorMessage);
        }
    }

    private ChannelTaskResponse mapToResponse(ChannelTask task) {
        return ChannelTaskResponse.builder()
                .id(task.getId())
                .groupId(task.getGroup() != null ? task.getGroup().getId() : null)
                .channelId(task.getChannel() != null ? task.getChannel().getId() : null)
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .createdById(task.getCreatedBy() != null ? task.getCreatedBy().getId() : null)
                .createdByUsername(task.getCreatedBy() != null ? task.getCreatedBy().getUsername() : null)
                .assignees(mapAssignees(task.getAssignees()))
                .dueAt(task.getDueAt() != null ? task.getDueAt().atZone(ZoneId.systemDefault()).toInstant().toString() : null)
                .completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private List<ChannelTaskAssigneeResponse> mapAssignees(Set<User> assignees) {
        if (assignees == null) {
            return List.of();
        }
        return assignees.stream()
                .map(user -> ChannelTaskAssigneeResponse.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .toList();
    }
}
