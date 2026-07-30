package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.response.MessageResponse;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.service.MediaAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler that automatically recalls (self-destructs) messages whose expiresAt
 * timestamp has passed. Runs every 2 seconds and pushes WebSocket updates so
 * connected clients update in real-time.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageSelfDestructScheduler {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MediaAuthorizationService mediaAuthorizationService;

    @Scheduled(fixedDelay = 2_000L)
    @Transactional
    public void recallExpiredMessages() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Message> expired = messageRepository.findExpiredMessages(now);
            if (expired.isEmpty()) return;

            log.debug("[SelfDestruct] Recalling {} expired message(s)", expired.size());

            for (Message message : expired) {
                // Skip system and poll messages
                if (message.getMessageType() == MessageType.SYSTEM
                        || message.getMessageType() == MessageType.POLL) {
                    continue;
                }

                List<String> attachmentUrls = message.getAttachments() == null
                        ? List.of()
                        : message.getAttachments().stream()
                                .filter(java.util.Objects::nonNull)
                                .map(iuh.fit.se.nextalk_be.entity.MessageAttachment::getUrl)
                                .filter(java.util.Objects::nonNull)
                                .distinct()
                                .toList();
                message.setRecalled(true);
                message.setContent("Tin nhắn tự hủy đã được xóa");
                message.setAttachments(new java.util.ArrayList<>());
                message.setMetadata(java.util.Map.of());
                message.setReactions(new java.util.ArrayList<>());
                message.setParentId(null);
                message.setForwardedFromMessageId(null);
                message.setForwardedFromSenderUsername(null);
                Message saved = messageRepository.save(message);
                attachmentUrls.forEach(mediaAuthorizationService::queueDeletionIfUnreferenced);

                // Build a lightweight response to broadcast
                MessageResponse response = buildRecallResponse(saved);

                // Resolve conversation to reach members list
                Conversation conv = saved.getConversation();
                if (conv == null && saved.getConversationId() != null) {
                    conv = conversationRepository.findById(saved.getConversationId()).orElse(null);
                }

                // Push to all conversation members via WebSocket
                if (conv != null && conv.getMembers() != null) {
                    conv.getMembers().forEach(member ->
                            messagingTemplate.convertAndSendToUser(
                                    member.getUsername(),
                                    "/queue/private",
                                    response
                            )
                    );
                }
            }
        } catch (Exception e) {
            log.error("[SelfDestruct] Error while recalling expired messages: {}", e.getMessage(), e);
        }
    }

    private MessageResponse buildRecallResponse(Message message) {
        String conversationId = message.getConversationId();
        if (conversationId == null && message.getConversation() != null) {
            conversationId = message.getConversation().getId();
        }
        String senderId = message.getSenderId();
        if (senderId == null && message.getSender() != null) {
            senderId = message.getSender().getId();
        }
        String senderUsername = message.getSenderUsername();
        if (senderUsername == null && message.getSender() != null) {
            senderUsername = message.getSender().getUsername();
        }
        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(conversationId)
                .senderId(senderId)
                .senderUsername(senderUsername)
                .content("Tin nhắn tự hủy đã được xóa")
                .messageType(message.getMessageType() != null ? message.getMessageType().name() : null)
                .isRecalled(true)
                .createdAt(message.getCreatedAt())
                .expiresAt(message.getExpiresAt())
                .build();
    }
}

