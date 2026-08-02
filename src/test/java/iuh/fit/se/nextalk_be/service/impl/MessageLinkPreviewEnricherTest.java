package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.response.LinkPreviewResponse;
import iuh.fit.se.nextalk_be.dto.response.LinkPreviewType;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.service.LinkPreviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageLinkPreviewEnricherTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private LinkPreviewService linkPreviewService;

    @InjectMocks
    private MessageLinkPreviewEnricher enricher;

    @Test
    void enrich_AddsPreviewAfterFetchAndPreservesExistingMetadata() {
        String content = "https://www.youtube.com/watch?v=video-id";
        Message message = textMessage("message-1", content, Map.of("clientMessageId", "client-1"));
        LinkPreviewResponse preview = LinkPreviewResponse.builder()
                .version(2)
                .url(content)
                .type(LinkPreviewType.VIDEO)
                .build();

        when(messageRepository.findById("message-1"))
                .thenReturn(Optional.of(message), Optional.of(message));
        when(linkPreviewService.createPreview(content)).thenReturn(Optional.of(preview));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Message> result = enricher.enrich("message-1", content);

        assertTrue(result.isPresent());
        assertEquals("client-1", result.orElseThrow().getMetadata().get("clientMessageId"));
        assertEquals(preview, result.orElseThrow().getMetadata().get("linkPreview"));
        assertFalse(result.orElseThrow().getMetadata().containsKey("realtimeEvent"));
        verify(messageRepository).save(message);
    }

    @Test
    void enrich_DoesNotOverwriteMessageEditedWhilePreviewWasLoading() {
        String originalContent = "https://www.youtube.com/watch?v=video-id";
        Message original = textMessage("message-2", originalContent, Map.of());
        Message edited = textMessage("message-2", "No link anymore", Map.of());
        LinkPreviewResponse preview = LinkPreviewResponse.builder()
                .version(2)
                .url(originalContent)
                .type(LinkPreviewType.VIDEO)
                .build();

        when(messageRepository.findById("message-2"))
                .thenReturn(Optional.of(original), Optional.of(edited));
        when(linkPreviewService.createPreview(originalContent)).thenReturn(Optional.of(preview));

        assertTrue(enricher.enrich("message-2", originalContent).isEmpty());
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void enrich_SkipsRecalledMessagesBeforePerformingNetworkWork() {
        String content = "https://example.com/article";
        Message recalled = textMessage("message-3", content, Map.of());
        recalled.setRecalled(true);
        when(messageRepository.findById("message-3")).thenReturn(Optional.of(recalled));

        assertTrue(enricher.enrich("message-3", content).isEmpty());
        verify(linkPreviewService, never()).createPreview(any());
        verify(messageRepository, never()).save(any(Message.class));
    }

    private Message textMessage(String id, String content, Map<String, Object> metadata) {
        Message message = Message.builder()
                .content(content)
                .messageType(MessageType.TEXT)
                .metadata(metadata)
                .build();
        message.setId(id);
        return message;
    }
}
