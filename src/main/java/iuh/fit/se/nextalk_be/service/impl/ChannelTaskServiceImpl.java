package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.request.CreateChannelTaskRequest;
import iuh.fit.se.nextalk_be.dto.request.UpdateChannelTaskRequest;
import iuh.fit.se.nextalk_be.dto.response.ChannelTaskAssigneeResponse;
import iuh.fit.se.nextalk_be.dto.response.ChannelTaskResponse;
import iuh.fit.se.nextalk_be.dto.response.TaskSourceMessageResponse;
import iuh.fit.se.nextalk_be.entity.*;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.ChannelTaskRepository;
import iuh.fit.se.nextalk_be.repository.GroupMemberRepository;
import iuh.fit.se.nextalk_be.repository.GroupRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.ChannelTaskService;
import iuh.fit.se.nextalk_be.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.jsoup.Jsoup;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChannelTaskServiceImpl implements ChannelTaskService {

    private final ChannelTaskRepository channelTaskRepository;
    private final ChannelRepository channelRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final UserService userService;
    private final iuh.fit.se.nextalk_be.service.ChannelTaskActivityService taskActivityService;

    @Override
    public List<ChannelTaskResponse> getTasks(String groupId, String channelId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Channel channel = getAccessibleChannel(groupId, channelId, currentUser);

        return channelTaskRepository.findAllByChannelIdOrderByCreatedAtDesc(channel.getId())
                .stream()
                .sorted((t1, t2) -> {
                    if (t1.isPinned() != t2.isPinned()) {
                        return Boolean.compare(t2.isPinned(), t1.isPinned());
                    }
                    if (t1.isPinned() && t1.getPinnedAt() != null && t2.getPinnedAt() != null) {
                        return t2.getPinnedAt().compareTo(t1.getPinnedAt());
                    }
                    return t2.getCreatedAt().compareTo(t1.getCreatedAt());
                })
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
                .startAt(parseOptionalDateTime(request.getStartAt(), "Invalid start date"))
                .dueAt(parseOptionalDateTime(request.getDueAt(), "Invalid due date"))
                .assignees(resolveAssignees(groupId, channel, request.getAssigneeIds()))
                .build();

        if (request.getSourceMessageId() != null && !request.getSourceMessageId().isBlank()) {
            task.setSourceMessage(resolveSourceMessage(channel, currentUser, request.getSourceMessageId()));
        }

        if (request.getSubtasks() != null) {
            List<Subtask> subtasks = request.getSubtasks().stream()
                    .filter(s -> s.getTitle() != null && !s.getTitle().isBlank())
                    .map(s -> Subtask.builder()
                            .id(s.getId() != null && !s.getId().isBlank() ? s.getId() : UUID.randomUUID().toString())
                            .title(s.getTitle().trim())
                            .isCompleted(Boolean.TRUE.equals(s.getIsCompleted()))
                            .build())
                    .collect(Collectors.toList());
            task.setSubtasks(subtasks);
        }
        if (request.getAttachments() != null) {
            List<iuh.fit.se.nextalk_be.entity.TaskAttachment> attachments = request.getAttachments().stream()
                    .filter(a -> a.getUrl() != null && !a.getUrl().isBlank())
                    .map(a -> iuh.fit.se.nextalk_be.entity.TaskAttachment.builder()
                            .id(a.getId() != null && !a.getId().isBlank() ? a.getId() : UUID.randomUUID().toString())
                            .url(a.getUrl().trim())
                            .name(a.getName() != null ? a.getName().trim() : "File")
                            .type(a.getType() != null ? a.getType().trim() : "FILE")
                            .size(a.getSize())
                            .build())
                    .collect(Collectors.toList());
            task.setAttachments(attachments);
        }

        if (task.getStatus() == ChannelTaskStatus.DONE) {
            task.setCompletedAt(LocalDateTime.now());
        }

        validateSchedule(task);
        ChannelTask savedTask = channelTaskRepository.save(task);
        try {
            taskActivityService.logActivity(groupId, channelId, savedTask.getId(), currentUser, iuh.fit.se.nextalk_be.entity.TaskActivityType.TASK_CREATED, "đã tạo công việc \"" + savedTask.getTitle() + "\".");
        } catch (Exception ignored) {}

        return mapToResponse(savedTask);
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
        if (request.getStartAt() != null) {
            task.setStartAt(parseOptionalDateTime(request.getStartAt(), "Invalid start date"));
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
        if (request.getSubtasks() != null) {
            List<Subtask> subtasks = request.getSubtasks().stream()
                    .filter(s -> s.getTitle() != null && !s.getTitle().isBlank())
                    .map(s -> Subtask.builder()
                            .id(s.getId() != null && !s.getId().isBlank() ? s.getId() : UUID.randomUUID().toString())
                            .title(s.getTitle().trim())
                            .isCompleted(Boolean.TRUE.equals(s.getIsCompleted()))
                            .build())
                    .collect(Collectors.toList());
            task.setSubtasks(subtasks);
            try {
                taskActivityService.logActivity(groupId, channelId, task.getId(), currentUser, iuh.fit.se.nextalk_be.entity.TaskActivityType.SUBTASK_COMPLETED, "đã cập nhật công việc phụ trong \"" + task.getTitle() + "\".");
            } catch (Exception ignored) {}
        }
        if (request.getAttachments() != null) {
            List<iuh.fit.se.nextalk_be.entity.TaskAttachment> attachments = request.getAttachments().stream()
                    .filter(a -> a.getUrl() != null && !a.getUrl().isBlank())
                    .map(a -> iuh.fit.se.nextalk_be.entity.TaskAttachment.builder()
                            .id(a.getId() != null && !a.getId().isBlank() ? a.getId() : UUID.randomUUID().toString())
                            .url(a.getUrl().trim())
                            .name(a.getName() != null ? a.getName().trim() : "File")
                            .type(a.getType() != null ? a.getType().trim() : "FILE")
                            .size(a.getSize())
                            .build())
                    .collect(Collectors.toList());
            task.setAttachments(attachments);
        }

        validateSchedule(task);
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
        try {
            taskActivityService.logActivity(groupId, channelId, task.getId(), currentUser, iuh.fit.se.nextalk_be.entity.TaskActivityType.STATUS_CHANGED, "đã chuyển trạng thái công việc \"" + task.getTitle() + "\" sang " + status.name() + ".");
        } catch (Exception ignored) {}
        return mapToResponse(channelTaskRepository.save(task));
    }

    @Override
    public ChannelTaskResponse togglePinTask(String groupId, String channelId, String taskId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Channel channel = getAccessibleChannel(groupId, channelId, currentUser);
        ChannelTask task = getTaskInChannel(taskId, channel);

        ensureCanModifyTask(groupId, task, currentUser);

        boolean nextPinned = !task.isPinned();
        task.setPinned(nextPinned);
        task.setPinnedAt(nextPinned ? LocalDateTime.now() : null);
        task.setUpdatedAt(LocalDateTime.now());

        ChannelTask saved = channelTaskRepository.save(task);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                String msg = nextPinned ? "đã ghim công việc \"" + task.getTitle() + "\"." : "đã bỏ ghim công việc \"" + task.getTitle() + "\".";
                taskActivityService.logActivity(groupId, channelId, taskId, currentUser, iuh.fit.se.nextalk_be.entity.TaskActivityType.ASSIGNEE_UPDATED, msg);
            } catch (Exception ignored) {}
        });

        return mapToResponse(saved);
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

    private void validateSchedule(ChannelTask task) {
        if (task.getStartAt() != null && task.getDueAt() != null && task.getDueAt().isBefore(task.getStartAt())) {
            throw new BadRequestException("Due date must not be before start date");
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
                .startAt(task.getStartAt() != null ? task.getStartAt().atZone(ZoneId.systemDefault()).toInstant().toString() : null)
                .dueAt(task.getDueAt() != null ? task.getDueAt().atZone(ZoneId.systemDefault()).toInstant().toString() : null)
                .completedAt(task.getCompletedAt())
                .subtasks(mapSubtasks(task.getSubtasks()))
                .attachments(mapAttachments(task.getAttachments()))
                .sourceMessage(mapSourceMessage(task.getSourceMessage()))
                .isPinned(task.isPinned())
                .pinnedAt(task.getPinnedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private TaskSourceMessage resolveSourceMessage(Channel channel, User currentUser, String sourceMessageId) {
        Message message = messageRepository.findById(sourceMessageId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Source message not found"));

        String channelConversationId = channel.getConversation() != null ? channel.getConversation().getId() : null;
        String messageConversationId = message.getConversationId();
        if (messageConversationId == null && message.getConversation() != null) {
            messageConversationId = message.getConversation().getId();
        }
        if (channelConversationId == null || !channelConversationId.equals(messageConversationId)) {
            throw new BadRequestException("Source message does not belong to this channel");
        }
        if (message.isRecalled()
                || (message.getDeletedByUsers() != null && message.getDeletedByUsers().contains(currentUser.getId()))) {
            throw new BadRequestException("Source message is no longer available");
        }

        String preview = Jsoup.parse(message.getContent() != null ? message.getContent() : "").text().trim();
        if (preview.isBlank() && message.getAttachments() != null && !message.getAttachments().isEmpty()) {
            MessageAttachment attachment = message.getAttachments().get(0);
            preview = attachment.getName() != null && !attachment.getName().isBlank()
                    ? attachment.getName().trim()
                    : "Attachment";
        }
        if (preview.length() > 500) {
            preview = preview.substring(0, 500);
        }

        return TaskSourceMessage.builder()
                .messageId(message.getId())
                .conversationId(messageConversationId)
                .channelId(channel.getId())
                .senderId(message.getSenderId() != null ? message.getSenderId()
                        : message.getSender() != null ? message.getSender().getId() : null)
                .senderUsername(message.getSenderUsername() != null ? message.getSenderUsername()
                        : message.getSender() != null ? message.getSender().getUsername() : null)
                .preview(preview)
                .createdAt(message.getCreatedAt())
                .build();
    }

    private TaskSourceMessageResponse mapSourceMessage(TaskSourceMessage source) {
        if (source == null) {
            return null;
        }
        return TaskSourceMessageResponse.builder()
                .messageId(source.getMessageId())
                .conversationId(source.getConversationId())
                .channelId(source.getChannelId())
                .senderId(source.getSenderId())
                .senderUsername(source.getSenderUsername())
                .preview(source.getPreview())
                .createdAt(source.getCreatedAt())
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

    private List<iuh.fit.se.nextalk_be.dto.response.SubtaskResponse> mapSubtasks(List<Subtask> subtasks) {
        if (subtasks == null) {
            return List.of();
        }
        return subtasks.stream()
                .map(s -> iuh.fit.se.nextalk_be.dto.response.SubtaskResponse.builder()
                        .id(s.getId())
                        .title(s.getTitle())
                        .isCompleted(s.isCompleted())
                        .build())
                .collect(Collectors.toList());
    }

    private List<iuh.fit.se.nextalk_be.dto.response.TaskAttachmentResponse> mapAttachments(List<iuh.fit.se.nextalk_be.entity.TaskAttachment> attachments) {
        if (attachments == null) {
            return List.of();
        }
        return attachments.stream()
                .map(a -> iuh.fit.se.nextalk_be.dto.response.TaskAttachmentResponse.builder()
                        .id(a.getId())
                        .url(a.getUrl())
                        .name(a.getName())
                        .type(a.getType())
                        .size(a.getSize())
                        .build())
                .collect(Collectors.toList());
    }
}
