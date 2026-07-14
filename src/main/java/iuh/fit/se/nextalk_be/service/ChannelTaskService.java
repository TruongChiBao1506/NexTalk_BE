package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.request.CreateChannelTaskRequest;
import iuh.fit.se.nextalk_be.dto.request.UpdateChannelTaskRequest;
import iuh.fit.se.nextalk_be.dto.response.ChannelTaskResponse;
import iuh.fit.se.nextalk_be.entity.ChannelTaskStatus;

import java.util.List;

public interface ChannelTaskService {
    List<ChannelTaskResponse> getTasks(String groupId, String channelId);
    ChannelTaskResponse createTask(String groupId, String channelId, CreateChannelTaskRequest request);
    ChannelTaskResponse updateTask(String groupId, String channelId, String taskId, UpdateChannelTaskRequest request);
    ChannelTaskResponse updateStatus(String groupId, String channelId, String taskId, ChannelTaskStatus status);
    void deleteTask(String groupId, String channelId, String taskId);
}
