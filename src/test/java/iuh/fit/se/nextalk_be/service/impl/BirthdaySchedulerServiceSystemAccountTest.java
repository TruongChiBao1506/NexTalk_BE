package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.MessageStatusRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BirthdaySchedulerServiceSystemAccountTest {

    @Mock private UserRepository userRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private MessageStatusRepository messageStatusRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private BirthdaySchedulerService service;

    @BeforeEach
    void setUp() {
        service = new BirthdaySchedulerService(
                userRepository,
                conversationRepository,
                messageRepository,
                messageStatusRepository,
                messagingTemplate);
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void newlyCreatedModeratorIsLockedAndExcludedFromDiscovery() {
        when(userRepository.findByEmail("moderator@nextalk.local"))
                .thenReturn(Optional.empty());

        User moderator = ReflectionTestUtils.invokeMethod(service, "getOrCreateBot");

        assertNotNull(moderator);
        assertTrue(moderator.isSystemAccount());
        assertTrue(moderator.isAccountLocked());
        assertFalse(moderator.isFriendSuggestionDiscoverable());
        assertNotNull(moderator.getPassword());
        assertTrue(moderator.getPassword().length() > 50);
        verify(userRepository).save(moderator);
    }

    @Test
    void existingModeratorIsMigratedWhenSchedulerUsesIt() {
        User moderator = User.builder()
                .username("NexTalk Moderator")
                .email("moderator@nextalk.local")
                .friendSuggestionDiscoverable(true)
                .systemAccount(false)
                .isAccountLocked(false)
                .build();
        moderator.setId("system-user");
        when(userRepository.findByEmail("moderator@nextalk.local"))
                .thenReturn(Optional.of(moderator));

        User result = ReflectionTestUtils.invokeMethod(service, "getOrCreateBot");

        assertTrue(result.isSystemAccount());
        assertTrue(result.isAccountLocked());
        assertFalse(result.isFriendSuggestionDiscoverable());
        verify(userRepository).save(moderator);
    }
}
