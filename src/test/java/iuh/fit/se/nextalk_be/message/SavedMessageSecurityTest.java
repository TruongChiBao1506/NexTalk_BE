package iuh.fit.se.nextalk_be.message;

import iuh.fit.se.nextalk_be.dto.response.MessageResponse;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.SavedMessage;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.SavedMessageRepository;
import iuh.fit.se.nextalk_be.service.MessageService;
import iuh.fit.se.nextalk_be.service.SavedMessageService;
import iuh.fit.se.nextalk_be.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavedMessageSecurityTest {
    @Mock SavedMessageRepository savedMessageRepository;
    @Mock MessageRepository messageRepository;
    @Mock MessageService messageService;
    @Mock UserService userService;
    @InjectMocks SavedMessageService service;

    @Test
    void saveRejectsMessageOutsideCurrentUsersConversations() {
        User user = user("user-a");
        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);
        when(messageService.getMessageForCurrentUser("message-b"))
                .thenThrow(new BadRequestException("not a member"));

        assertThrows(BadRequestException.class, () -> service.save("message-b"));
        verify(savedMessageRepository, never()).save(any());
    }

    @Test
    void savedListDoesNotRestoreAccessAfterUserLeavesConversation() {
        User user = user("user-a");
        Message message = new Message();
        message.setId("message-b");
        SavedMessage saved = SavedMessage.builder().user(user).message(message).build();
        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);
        when(savedMessageRepository.findAllByUserIdOrderByCreatedAtDesc(eq("user-a"), any(Pageable.class)))
                .thenReturn(List.of(saved));
        when(messageService.getMessageForCurrentUser("message-b"))
                .thenThrow(new BadRequestException("not a member"));

        assertTrue(service.getMine(50).isEmpty());
    }

    @Test
    void removeUsesAuthenticatedUserInsteadOfClientSuppliedOwner() {
        User user = user("user-a");
        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);

        service.remove("message-1");

        verify(savedMessageRepository).deleteByUserIdAndMessageId("user-a", "message-1");
    }

    private User user(String id) {
        User user = User.builder().username("tester").email("tester@example.invalid").build();
        user.setId(id);
        return user;
    }
}
