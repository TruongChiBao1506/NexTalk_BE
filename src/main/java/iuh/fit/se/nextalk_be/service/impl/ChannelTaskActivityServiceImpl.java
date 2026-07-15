package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.response.TaskActivityResponse;
import iuh.fit.se.nextalk_be.entity.*;
import iuh.fit.se.nextalk_be.repository.*;
import iuh.fit.se.nextalk_be.service.ChannelTaskActivityService;
import iuh.fit.se.nextalk_be.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChannelTaskActivityServiceImpl implements ChannelTaskActivityService {

    private final ChannelTaskActivityRepository activityRepository;
    private final ChannelTaskRepository taskRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserService userService;
    private final iuh.fit.se.nextalk_be.service.FCMService fcmService;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public List<TaskActivityResponse> getActivities(String groupId, String channelId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        List<ChannelTaskActivity> activities = activityRepository
                .findAllByGroupIdAndChannelIdOrderByCreatedAtDesc(groupId, channelId);

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
        List<ChannelTaskActivity> activities = activityRepository
                .findAllByGroupIdAndChannelIdOrderByCreatedAtDesc(groupId, channelId);

        boolean updated = false;
        for (ChannelTaskActivity act : activities) {
            if (act.getReadByUserIds() == null) {
                act.setReadByUserIds(new HashSet<>());
            }
            if (act.getReadByUserIds().add(currentUser.getId())) {
                updated = true;
            }
        }

        if (updated) {
            activityRepository.saveAll(activities);
        }
    }

    // Cron job running every 1 minute to check due dates and auto-log alerts
    @Scheduled(fixedRate = 60000)
    public void scanAndRemindTasks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourLater = now.plusHours(1);

        List<ChannelTask> activeTasks = taskRepository.findAll().stream()
                .filter(task -> task.getStatus() != ChannelTaskStatus.DONE && task.getStatus() != ChannelTaskStatus.CANCELLED)
                .filter(task -> task.getDueAt() != null)
                .toList();

        for (ChannelTask task : activeTasks) {
            try {
                if (task.getGroup() == null || task.getChannel() == null) continue;

                String groupId = task.getGroup().getId();
                String channelId = task.getChannel().getId();

                // Check if overdue
                if (task.getDueAt().isBefore(now)) {
                    boolean alreadyOverdueLogged = activityRepository
                            .findAllByGroupIdAndChannelIdOrderByCreatedAtDesc(groupId, channelId)
                            .stream()
                            .anyMatch(a -> task.getId().equals(a.getTaskId()) && a.getType() == TaskActivityType.TASK_OVERDUE);

                    if (!alreadyOverdueLogged) {
                        logActivity(
                                groupId,
                                channelId,
                                task.getId(),
                                null,
                                TaskActivityType.TASK_OVERDUE,
                                "🔴 Công việc \"" + task.getTitle() + "\" đã quá hạn chót!"
                        );
                    }
                }
                // Check if approaching due date (within 1 hour)
                else if (task.getDueAt().isBefore(oneHourLater)) {
                    boolean alreadyApproachingLogged = activityRepository
                            .findAllByGroupIdAndChannelIdOrderByCreatedAtDesc(groupId, channelId)
                            .stream()
                            .anyMatch(a -> task.getId().equals(a.getTaskId()) && a.getType() == TaskActivityType.DUE_APPROACHING);

                    if (!alreadyApproachingLogged) {
                        logActivity(
                                groupId,
                                channelId,
                                task.getId(),
                                null,
                                TaskActivityType.DUE_APPROACHING,
                                "⚠️ Công việc \"" + task.getTitle() + "\" sẽ hết hạn chót trong 1 giờ nữa!"
                        );
                    }
                }
            } catch (Exception ignored) {
                // Ignore single task resolution exception
            }
        }
    }

    private TaskActivityResponse mapToResponse(ChannelTaskActivity activity, String currentUserId) {
        boolean isRead = activity.getReadByUserIds() != null && activity.getReadByUserIds().contains(currentUserId);
        return TaskActivityResponse.builder()
                .id(activity.getId())
                .groupId(activity.getGroupId())
                .channelId(activity.getChannelId())
                .taskId(activity.getTaskId())
                .actorId(activity.getActor() != null ? activity.getActor().getId() : null)
                .actorUsername(activity.getActor() != null ? activity.getActor().getUsername() : "Hệ thống")
                .actorAvatarUrl(activity.getActor() != null ? activity.getActor().getAvatarUrl() : null)
                .type(activity.getType())
                .content(activity.getContent())
                .isRead(isRead)
                .createdAt(activity.getCreatedAt())
                .build();
    }
}
