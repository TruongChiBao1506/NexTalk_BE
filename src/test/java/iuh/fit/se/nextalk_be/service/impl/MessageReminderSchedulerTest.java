package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.MessageReminder;
import iuh.fit.se.nextalk_be.entity.MessageReminderStatus;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.MessageReminderRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.FCMService;
import iuh.fit.se.nextalk_be.service.NotificationService;
import iuh.fit.se.nextalk_be.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageReminderSchedulerTest {

    @Test
    void dispatchesOnlyOneBoundedOrderedBatch() {
        MessageReminderRepository reminders = mock(MessageReminderRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        UserRepository users = mock(UserRepository.class);
        UserService userService = mock(UserService.class);
        NotificationService notifications = mock(NotificationService.class);
        FCMService fcm = mock(FCMService.class);

        User recipient = User.builder().username("recipient").fcmTokens(List.of()).build();
        recipient.setId("user-1");
        Conversation conversation = Conversation.builder().build();
        conversation.setId("conversation-1");
        MessageReminder reminder = MessageReminder.builder()
                .user(recipient)
                .conversation(conversation)
                .status(MessageReminderStatus.PENDING)
                .remindAt(LocalDateTime.now().minusMinutes(1))
                .messagePreview("preview")
                .senderUsername("sender")
                .build();
        when(reminders.findByStatusAndRemindAtLessThanEqualOrderByRemindAtAsc(
                eq(MessageReminderStatus.PENDING),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(reminder));
        when(users.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(reminders.save(reminder)).thenReturn(reminder);

        new MessageReminderServiceImpl(
                reminders,
                messages,
                users,
                userService,
                notifications,
                fcm
        ).dispatchDueReminders();

        verify(reminders).findByStatusAndRemindAtLessThanEqualOrderByRemindAtAsc(
                eq(MessageReminderStatus.PENDING),
                any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.argThat(pageable ->
                        pageable.getPageNumber() == 0 && pageable.getPageSize() == 100)
        );
        assertEquals(MessageReminderStatus.FIRED, reminder.getStatus());
    }
}
