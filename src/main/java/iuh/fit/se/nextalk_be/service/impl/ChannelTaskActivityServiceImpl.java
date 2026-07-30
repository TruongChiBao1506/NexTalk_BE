package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.response.TaskActivityResponse;
import iuh.fit.se.nextalk_be.entity.*;
import iuh.fit.se.nextalk_be.repository.*;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.service.ChannelTaskActivityService;
import iuh.fit.se.nextalk_be.service.NotificationService;
import iuh.fit.se.nextalk_be.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChannelTaskActivityServiceImpl implements ChannelTaskActivityService {
    private static final int DEADLINE_BATCH_SIZE = 100;
    private static final List<ChannelTaskStatus> ACTIVE_STATUSES =
            List.of(ChannelTaskStatus.TODO, ChannelTaskStatus.IN_PROGRESS);

    private final ChannelTaskActivityRepository activityRepository;
    private final ChannelTaskRepository taskRepository;
    private final ChannelRepository channelRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserService userService;
    private final iuh.fit.se.nextalk_be.service.FCMService fcmService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    @Override
    public List<TaskActivityResponse> getActivities(String groupId, String channelId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        requireAccessibleChannel(groupId, channelId, currentUser);
        List<ChannelTaskActivity> activities = activityRepository
                .findTop200ByGroupIdAndChannelIdOrderByCreatedAtDesc(groupId, channelId);

        return activities.stream()
                .map(act -> mapToResponse(act, currentUser.getId()))
                .collect(Collectors.toList());
    }

    @Override
    public void logActivity(String groupId, String channelId, String taskId, User actor, TaskActivityType type, String content) {
        Set<String> readSet = new HashSet<>();
        if (actor != null) {
            readSet.add(actor.getId());
        }

        ChannelTaskActivity activity = ChannelTaskActivity.builder()
                .groupId(groupId)
                .channelId(channelId)
                .taskId(taskId)
                .actor(actor)
                .type(type)
                .content(content)
                .readByUserIds(readSet)
                .build();

        ChannelTaskActivity saved = activityRepository.save(activity);
        TaskActivityResponse response = mapToResponse(saved, actor != null ? actor.getId() : "");

        // Broadcast realtime websocket notification
        try {
            messagingTemplate.convertAndSend("/topic/channel." + channelId + ".task-activities", response);
        } catch (Exception ignored) {
            // Ignore socket delivery errors if offline
        }

        // Send FCM Push Notifications to Group members
        try {
            List<GroupMember> members = groupMemberRepository.findAllByGroupId(groupId);
            if (members != null && !members.isEmpty()) {
                List<String> fcmTokens = members.stream()
                        .map(GroupMember::getUser)
                        .filter(u -> u != null && (actor == null || !u.getId().equals(actor.getId())))
                        .filter(u -> u.getFcmTokens() != null && !u.getFcmTokens().isEmpty())
                        .flatMap(u -> u.getFcmTokens().stream())
                        .distinct()
                        .toList();

                if (!fcmTokens.isEmpty()) {
                    String actorName = actor != null ? actor.getUsername() : "Hệ thống";
                    fcmService.sendPushNotificationToTokens(fcmTokens, "Thông báo công việc NexTalk", actorName + " " + content);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void markAllAsRead(String groupId, String channelId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        requireAccessibleChannel(groupId, channelId, currentUser);
        activityRepository.markAllAsRead(groupId, channelId, currentUser.getId());
    }

    private void requireAccessibleChannel(String groupId, String channelId, User user) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found"));
        if (channel.getGroup() == null || !groupId.equals(channel.getGroup().getId())) {
            throw new BadRequestException("Channel does not belong to this group");
        }

        boolean isOwner = group.getOwner() != null && user.getId().equals(group.getOwner().getId());
        boolean isMember = groupMemberRepository.existsByGroupIdAndUserId(groupId, user.getId());
        if (!isOwner && !isMember) {
            throw new UnauthorizedException("You are not a member of this group");
        }

        Conversation conversation = channel.getConversation();
        boolean isPrivateChannelMember = conversation != null
                && conversation.getMembers() != null
                && conversation.getMembers().stream()
                .anyMatch(member -> member != null && user.getId().equals(member.getId()));
        if (channel.isPrivate() && !isPrivateChannelMember) {
            throw new UnauthorizedException("You are not a member of this channel");
        }
    }

    // Cron job running every 1 minute to check due dates and auto-log alerts
    @Scheduled(fixedRate = 60000)
    public void scanAndRemindTasks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourLater = now.plusHours(1);

        List<ChannelTask> activeTasks = taskRepository.findDeadlineCandidates(
                ACTIVE_STATUSES,
                now,
                oneHourLater,
                PageRequest.of(0, DEADLINE_BATCH_SIZE)
        );

        for (ChannelTask task : activeTasks) {
            try {
                if (task.getGroup() == null || task.getChannel() == null) continue;

                String groupId = task.getGroup().getId();
                String channelId = task.getChannel().getId();

                if (task.getReminderAt() != null
                        && !task.isReminderSent()
                        && !task.getReminderAt().isAfter(now)) {
                    notifyTaskDeadline(
                            task,
                            "Đến giờ nhắc công việc \"" + task.getTitle() + "\"",
                            NotificationType.TASK_DUE
                    );
                    task.setReminderSent(true);
                    task.setUpdatedAt(now);
                    taskRepository.save(task);
                }

                // Check if overdue
                if (task.getDueAt().isBefore(now)) {
                    boolean alreadyOverdueLogged = activityRepository
                            .existsByTaskIdAndType(task.getId(), TaskActivityType.TASK_OVERDUE);

                    if (!alreadyOverdueLogged) {
                        logActivity(
                                groupId,
                                channelId,
                                task.getId(),
                                null,
                                TaskActivityType.TASK_OVERDUE,
                                "🔴 Công việc \"" + task.getTitle() + "\" đã quá hạn chót!"
                        );
                        notifyTaskDeadline(task, "Công việc \"" + task.getTitle() + "\" đã quá hạn", NotificationType.TASK_DUE);
                    }
                    task.setOverdueNotificationSent(true);
                    task.setUpdatedAt(now);
                    taskRepository.save(task);
                }
                // Check if approaching due date (within 1 hour)
                else if (task.getDueAt().isBefore(oneHourLater)) {
                    boolean alreadyApproachingLogged = activityRepository
                            .existsByTaskIdAndType(task.getId(), TaskActivityType.DUE_APPROACHING);

                    if (!alreadyApproachingLogged) {
                        logActivity(
                                groupId,
                                channelId,
                                task.getId(),
                                null,
                                TaskActivityType.DUE_APPROACHING,
                                "⚠️ Công việc \"" + task.getTitle() + "\" sẽ hết hạn chót trong 1 giờ nữa!"
                        );
                        notifyTaskDeadline(task, "Công việc \"" + task.getTitle() + "\" sẽ hết hạn trong 1 giờ", NotificationType.TASK_DUE);
                    }
                    task.setApproachingNotificationSent(true);
                    task.setUpdatedAt(now);
                    taskRepository.save(task);
                }
            } catch (Exception ignored) {
                // Ignore single task resolution exception
            }
        }
    }

    private void notifyTaskDeadline(ChannelTask task, String content, NotificationType type) {
        Set<User> recipients = new HashSet<>();
        if (task.getAssignees() != null) {
            recipients.addAll(task.getAssignees());
        }
        if (task.getWatchers() != null) {
            recipients.addAll(task.getWatchers());
        }
        if (recipients.isEmpty() && task.getCreatedBy() != null) {
            recipients.add(task.getCreatedBy());
        }
        String conversationId = task.getChannel() != null && task.getChannel().getConversation() != null
                ? task.getChannel().getConversation().getId()
                : null;

        recipients.stream().filter(java.util.Objects::nonNull).forEach(recipient -> {
            try {
                notificationService.createAndSend(recipient, type, content, conversationId, task.getId());
            } catch (Exception ignored) {
                // Deadline scan must continue for the remaining tasks.
            }
        });
    }

    private TaskActivityResponse mapToResponse(ChannelTaskActivity activity, String currentUserId) {
        boolean isRead = activity.getReadByUserIds() != null && activity.getReadByUserIds().contains(currentUserId);
        String username = activity.getActor() != null && activity.getActor().getUsername() != null && !activity.getActor().getUsername().isBlank()
                ? activity.getActor().getUsername()
                : (activity.getType() == TaskActivityType.DUE_APPROACHING || activity.getType() == TaskActivityType.TASK_OVERDUE ? "Cảnh báo Deadline" : "Hệ thống");
        return TaskActivityResponse.builder()
                .id(activity.getId())
                .groupId(activity.getGroupId())
                .channelId(activity.getChannelId())
                .taskId(activity.getTaskId())
                .actorId(activity.getActor() != null ? activity.getActor().getId() : null)
                .actorUsername(username)
                .actorAvatarUrl(activity.getActor() != null ? activity.getActor().getAvatarUrl() : null)
                .type(activity.getType())
                .content(activity.getContent())
                .isRead(isRead)
                .createdAt(activity.getCreatedAt())
                .build();
    }
}
