package iuh.fit.se.nextalk_be.summary;

import iuh.fit.se.nextalk_be.conversation.Conversation;
import iuh.fit.se.nextalk_be.conversation.ConversationRepository;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.message.Message;
import iuh.fit.se.nextalk_be.message.MessageRepository;
import iuh.fit.se.nextalk_be.message.MessageType;
import iuh.fit.se.nextalk_be.summary.dto.ConversationSummaryRequest;
import iuh.fit.se.nextalk_be.summary.dto.ConversationSummaryResponse;
import iuh.fit.se.nextalk_be.summary.dto.SummaryMessagePayload;
import iuh.fit.se.nextalk_be.user.User;
import iuh.fit.se.nextalk_be.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RestTemplate restTemplate;

    @Value("${app.summary.n8n-webhook-url:}")
    private String n8nWebhookUrl;

    @Value("${app.summary.message-limit:15}")
    private int messageLimit;

    @Value("${app.summary.preferred-model:gemini-2.5-flash}")
    private String preferredModel;

    public ConversationSummaryResponse summarize(String conversationId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with ID: " + conversationId));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(member -> member.getId().equals(currentUser.getId()));
        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }
        if (n8nWebhookUrl == null || n8nWebhookUrl.isBlank()) {
            throw new BadRequestException("Conversation summary webhook is not configured");
        }

        List<SummaryMessagePayload> cleanMessages = messageRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, messageLimit))
                .getContent()
                .stream()
                .filter(this::isSummarizableMessage)
                .sorted(Comparator.comparing(Message::getCreatedAt))
                .map(message -> SummaryMessagePayload.builder()
                        .senderId(message.getSender().getId())
                        .senderUsername(message.getSender().getUsername())
                        .content(message.getContent().trim())
                        .createdAt(message.getCreatedAt())
                        .build())
                .toList();

        if (cleanMessages.isEmpty()) {
            throw new BadRequestException("Not enough readable messages to summarize");
        }

        ConversationSummaryRequest request = ConversationSummaryRequest.builder()
                .conversationId(conversation.getId())
                .conversationName(conversation.getName())
                .conversationType(conversation.getType().name())
                .requestedByUserId(currentUser.getId())
                .requestedByUsername(currentUser.getUsername())
                .messageCount(cleanMessages.size())
                .preferredModel(preferredModel)
                .instruction("Tóm tắt dưới 4 dòng, nêu chủ đề chính, quyết định đã chốt và task được giao nếu có.")
                .messages(cleanMessages)
                .build();

        ResponseEntity<Object> response = restTemplate.postForEntity(n8nWebhookUrl, request, Object.class);
        String summary = extractSummary(response.getBody());
        if (summary == null || summary.isBlank()) {
            throw new BadRequestException("Summary webhook did not return a summary");
        }

        ConversationSummaryResponse summaryResponse = ConversationSummaryResponse.builder()
                .conversationId(conversationId)
                .summary(summary.trim())
                .sourceMessageCount(cleanMessages.size())
                .createdAt(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSendToUser(
                currentUser.getUsername(),
                "/queue/private",
                summaryResponse
        );

        return summaryResponse;
    }

    private boolean isSummarizableMessage(Message message) {
        if (message.isRecalled() || message.getMessageType() == MessageType.SYSTEM) {
            return false;
        }
        if (message.getMessageType() != MessageType.TEXT || message.getContent() == null) {
            return false;
        }
        String content = message.getContent().trim();
        if (content.isBlank()) {
            return false;
        }
        return content.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private String extractSummary(Object body) {
        if (body == null) {
            return null;
        }
        if (body instanceof String text) {
            return text;
        }
        if (!(body instanceof Map<?, ?> map)) {
            return String.valueOf(body);
        }
        Object summary = map.get("summary");
        if (summary == null) summary = map.get("text");
        if (summary == null) summary = map.get("output");
        if (summary == null) summary = map.get("result");
        return summary != null ? String.valueOf(summary) : null;
    }
}
