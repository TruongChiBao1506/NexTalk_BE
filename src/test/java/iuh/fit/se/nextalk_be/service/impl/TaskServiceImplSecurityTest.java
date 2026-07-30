package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.request.TaskRequest;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.Task;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplSecurityTest {

    @Mock private TaskRepository taskRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private TaskServiceImpl service;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        service = new TaskServiceImpl(taskRepository, conversationRepository, messagingTemplate);
        User member = User.builder().email("member@example.test").username("member").build();
        member.setId("member-1");
        conversation = Conversation.builder().members(Set.of(member)).build();
        conversation.setId("conversation-1");
    }

    @Test
    void outsiderCannotCreateOrReadConversationTasks() {
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        TaskRequest request = new TaskRequest();
        request.setConversationId(conversation.getId());
        request.setName("Protected task");

        assertThrows(UnauthorizedException.class, () -> service.createTask("outsider-1", request));
        assertThrows(UnauthorizedException.class,
                () -> service.getTasksByConversation("outsider-1", conversation.getId()));

        verify(taskRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(taskRepository, never()).findByConversationId(conversation.getId());
    }

    @Test
    void outsiderCannotUpdateOrDeleteTaskEvenWhenTaskIdIsKnown() {
        Task task = Task.builder().conversationId(conversation.getId()).name("Protected task").build();
        task.setId("task-1");
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        assertThrows(UnauthorizedException.class,
                () -> service.updateTask("outsider-1", task.getId(), new TaskRequest()));
        assertThrows(UnauthorizedException.class,
                () -> service.deleteTask("outsider-1", task.getId()));

        verify(taskRepository, never()).delete(task);
        verify(taskRepository, never()).save(task);
    }

    @Test
    void assigneeMustBelongToConversation() {
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        TaskRequest request = new TaskRequest();
        request.setConversationId(conversation.getId());
        request.setName("Protected task");
        request.setAssigneeIds(List.of("outsider-1"));

        assertThrows(BadRequestException.class, () -> service.createTask("member-1", request));
        verify(taskRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
