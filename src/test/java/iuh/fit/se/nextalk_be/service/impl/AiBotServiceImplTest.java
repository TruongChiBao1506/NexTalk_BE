package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.MessageStatusRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.ConversationSummaryService;
import iuh.fit.se.nextalk_be.service.GeminiMultimodalService;
import iuh.fit.se.nextalk_be.service.MessageReminderService;
import iuh.fit.se.nextalk_be.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiBotServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void answerMentionPersistsReplyAsAiBotInsteadOfRequester() {
        MessageRepository messageRepository = mock(MessageRepository.class);
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageStatusRepository messageStatusRepository = mock(MessageStatusRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        AiBotServiceImpl service = new AiBotServiceImpl(
                messageRepository,
                conversationRepository,
                messageStatusRepository,
                userRepository,
                mock(SimpMessagingTemplate.class),
                mock(RestTemplate.class),
                mock(MessageReminderService.class),
                mock(ConversationSummaryService.class),
                mock(GeminiMultimodalService.class),
                mock(ObjectProvider.class)
        );

        User requester = User.builder().username("Lan").email("lan@example.com").build();
        requester.setId("requester-id");
        User member = User.builder().username("Minh").email("minh@example.com").build();
        member.setId("member-id");
        User bot = User.builder()
                .username("NexTalk AI")
                .email("assistant@nextalk.local")
                .isVerified(true)
                .build();
        bot.setId("bot-id");

        Conversation conversation = Conversation.builder()
                .type(ConversationType.GROUP)
                .members(new HashSet<>(List.of(requester, member)))
                .build();
        conversation.setId("conversation-id");

        Message trigger = Message.builder()
                .conversation(conversation)
                .sender(requester)
                .content("@NexTalk AI cho mình hỏi")
                .messageType(MessageType.TEXT)
                .build();
        trigger.setId("trigger-id");

        when(userRepository.findByEmail("assistant@nextalk.local")).thenReturn(Optional.of(bot));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message saved = invocation.getArgument(0);
            saved.setId("reply-id");
            return saved;
        });
        when(messageStatusRepository.findAllByMessageId(anyString())).thenReturn(List.of());

        service.answerMentionAsync(conversation, trigger, requester);

        var messageCaptor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        Message reply = messageCaptor.getValue();

        assertSame(bot, reply.getSender());
        assertEquals("bot-id", reply.getSenderId());
        assertEquals("NexTalk AI", reply.getSenderUsername());
        assertNotEquals(requester.getId(), reply.getSenderId());
        assertEquals(bot.getAvatarUrl(), reply.getMetadata().get("botAvatarUrl"));
        assertEquals("requester-id", reply.getMetadata().get("requestedByUserId"));
        assertEquals("trigger-id", reply.getMetadata().get("triggerMessageId"));
    }
}
