package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.response.TaskActivityResponse;
import iuh.fit.se.nextalk_be.entity.TaskActivityType;
import iuh.fit.se.nextalk_be.entity.User;

import java.util.List;

public interface ChannelTaskActivityService {
    List<TaskActivityResponse> getActivities(String groupId, String channelId);
    void logActivity(String groupId, String channelId, String taskId, User actor, TaskActivityType type, String content);
    void markAllAsRead(String groupId, String channelId);
}
