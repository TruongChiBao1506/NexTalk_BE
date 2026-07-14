package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.request.TaskRequest;
import iuh.fit.se.nextalk_be.dto.response.TaskResponse;

import java.util.List;

public interface TaskService {
    TaskResponse createTask(String creatorId, TaskRequest request);
    TaskResponse updateTask(String userId, String taskId, TaskRequest request);
    void deleteTask(String userId, String taskId);
    List<TaskResponse> getTasksByConversation(String conversationId);
}
