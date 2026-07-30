package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.request.TaskRequest;
import iuh.fit.se.nextalk_be.dto.response.TaskResponse;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.Task;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.TaskRepository;
import iuh.fit.se.nextalk_be.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final ConversationRepository conversationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public TaskResponse createTask(String creatorId, TaskRequest request) {
        Conversation conversation = requireConversationMember(request.getConversationId(), creatorId);
        validateAssignees(conversation, request.getAssigneeIds());

        Task task = Task.builder()
                .conversationId(request.getConversationId())
                .name(request.getName())
                .description(request.getDescription())
                .status(request.getStatus())
                .priority(request.getPriority())
                .assigneeIds(request.getAssigneeIds())
                .creatorId(creatorId)
                .startDate(request.getStartDate())
                .dueDate(request.getDueDate())
                .progress(request.getProgress() != null ? request.getProgress() : 0)
                .build();
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        Task savedTask = taskRepository.save(task);
        TaskResponse response = mapToResponse(savedTask);

        broadcastTaskUpdate(response.getConversationId(), "CREATED", response);

        return response;
    }

    @Override
    public TaskResponse updateTask(String userId, String taskId, TaskRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        Conversation conversation = requireConversationMember(task.getConversationId(), userId);

        if (request.getConversationId() != null
                && !task.getConversationId().equals(request.getConversationId())) {
            throw new BadRequestException("A task cannot be moved to another conversation");
        }
        validateAssignees(conversation, request.getAssigneeIds());

        if (request.getName() != null) task.setName(request.getName());
        if (request.getDescription() != null) task.setDescription(request.getDescription());
        if (request.getStatus() != null) task.setStatus(request.getStatus());
        if (request.getPriority() != null) task.setPriority(request.getPriority());
        if (request.getAssigneeIds() != null) task.setAssigneeIds(request.getAssigneeIds());
        if (request.getStartDate() != null) task.setStartDate(request.getStartDate());
        if (request.getDueDate() != null) task.setDueDate(request.getDueDate());
        if (request.getProgress() != null) task.setProgress(request.getProgress());

        task.setUpdatedAt(LocalDateTime.now());

        Task updatedTask = taskRepository.save(task);
        TaskResponse response = mapToResponse(updatedTask);

        broadcastTaskUpdate(response.getConversationId(), "UPDATED", response);

        return response;
    }

    @Override
    public void deleteTask(String userId, String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        requireConversationMember(task.getConversationId(), userId);

        taskRepository.delete(task);

        broadcastTaskUpdate(task.getConversationId(), "DELETED", mapToResponse(task));
    }

    @Override
    public List<TaskResponse> getTasksByConversation(String userId, String conversationId) {
        requireConversationMember(conversationId, userId);
        return taskRepository.findByConversationId(conversationId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private Conversation requireConversationMember(String conversationId, String userId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new BadRequestException("Conversation ID is required");
        }
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        boolean isMember = conversation.getMembers() != null
                && conversation.getMembers().stream()
                .anyMatch(member -> member != null && userId.equals(member.getId()));
        if (!isMember) {
            throw new UnauthorizedException("You are not a member of this conversation");
        }
        return conversation;
    }

    private void validateAssignees(Conversation conversation, List<String> assigneeIds) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return;
        }
        java.util.Set<String> memberIds = conversation.getMembers() == null
                ? java.util.Set.of()
                : conversation.getMembers().stream()
                .filter(java.util.Objects::nonNull)
                .map(member -> member.getId())
                .collect(java.util.stream.Collectors.toSet());
        if (assigneeIds.stream().anyMatch(id -> id == null || !memberIds.contains(id))) {
            throw new BadRequestException("Every task assignee must be a conversation member");
        }
    }

    private TaskResponse mapToResponse(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .conversationId(task.getConversationId())
                .name(task.getName())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .assigneeIds(task.getAssigneeIds())
                .creatorId(task.getCreatorId())
                .startDate(task.getStartDate())
                .dueDate(task.getDueDate())
                .progress(task.getProgress())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private void broadcastTaskUpdate(String conversationId, String type, TaskResponse task) {
        messagingTemplate.convertAndSend(
                "/topic/conversation." + conversationId + ".tasks",
                new TaskEventMessage(type, task)
        );
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class TaskEventMessage {
        private String type;
        private TaskResponse data;
    }
}
