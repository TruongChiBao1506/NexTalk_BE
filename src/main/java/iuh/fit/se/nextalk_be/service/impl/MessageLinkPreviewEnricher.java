package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.response.LinkPreviewResponse;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.service.LinkPreviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MessageLinkPreviewEnricher {

    private final MessageRepository messageRepository;
    private final LinkPreviewService linkPreviewService;

    public Optional<Message> enrich(String messageId, String expectedContent) {
        Optional<Message> initialMessage = messageRepository.findById(messageId);
        if (initialMessage.isEmpty() || !isStillEligible(initialMessage.get(), expectedContent)) {
            return Optional.empty();
        }

        Optional<LinkPreviewResponse> preview = linkPreviewService.createPreview(expectedContent);
        if (preview.isEmpty()) {
            return Optional.empty();
        }

        Optional<Message> latestMessage = messageRepository.findById(messageId);
        if (latestMessage.isEmpty() || !isStillEligible(latestMessage.get(), expectedContent)) {
            return Optional.empty();
        }

        Message message = latestMessage.get();
        Map<String, Object> metadata = message.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(message.getMetadata());
        metadata.put("linkPreview", preview.get());
        message.setMetadata(metadata);
        return Optional.of(messageRepository.save(message));
    }

    private boolean isStillEligible(Message message, String expectedContent) {
        Map<String, Object> metadata = message.getMetadata();
        return !message.isRecalled()
                && message.getMessageType() == MessageType.TEXT
                && expectedContent != null
                && expectedContent.equals(message.getContent())
                && (metadata == null || !Boolean.TRUE.equals(metadata.get("suppressLinkPreview")))
                && (metadata == null || !metadata.containsKey("linkPreview"));
    }
}
