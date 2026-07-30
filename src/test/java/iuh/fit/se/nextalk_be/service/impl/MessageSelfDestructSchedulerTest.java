package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageAttachment;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.service.MediaAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MessageSelfDestructSchedulerTest {
    @Test
    void purgesContentMetadataAndQueuesAttachmentDeletion() {
        MessageRepository messages = mock(MessageRepository.class);
        ConversationRepository conversations = mock(ConversationRepository.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        MediaAuthorizationService media = mock(MediaAuthorizationService.class);
        Message message = Message.builder()
                .content("secret")
                .messageType(MessageType.TEXT)
                .attachments(List.of(MessageAttachment.builder().url("protected-url").build()))
                .metadata(Map.of("secret", "value"))
                .build();
        when(messages.findExpiredMessages(any(), any(Pageable.class))).thenReturn(List.of(message));
        when(messages.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new MessageSelfDestructScheduler(messages, conversations, messaging, media)
                .recallExpiredMessages();

        assertTrue(message.isRecalled());
        assertTrue(message.getAttachments().isEmpty());
        assertTrue(message.getMetadata().isEmpty());
        assertTrue(message.getReactions().isEmpty());
        assertFalse(message.getContent().contains("secret"));
        verify(media).queueDeletionIfUnreferenced("protected-url");
        verify(messages).findExpiredMessages(any(), argThat(pageable ->
                pageable.getPageNumber() == 0 && pageable.getPageSize() == 100));
    }
}
