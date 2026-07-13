package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.ConversationSummaryService;

import iuh.fit.se.nextalk_be.dto.SummaryMessagePayload;
import iuh.fit.se.nextalk_be.dto.request.ConversationSummaryRequest;
import iuh.fit.se.nextalk_be.dto.response.ConversationSummaryResponse;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.service.UserService;
import iuh.fit.se.nextalk_be.security.RateLimitService;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConversationSummaryServiceImpl implements ConversationSummaryService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RestTemplate restTemplate;
    private final RateLimitService rateLimitService;

    @Value("${app.rate-limit.ai-summary.limit:3}")
    private int summaryRateLimit;

    @Value("${app.rate-limit.ai-summary.window-seconds:300}")
    private long summaryRateWindowSeconds;

    @Value("${app.summary.n8n-webhook-url:}")
    private String n8nWebhookUrl;

    @Value("${app.summary.message-limit:15}")
    private int messageLimit;

    @Value("${app.summary.preferred-model:gemini-2.5-flash}")
    private String preferredModel;

    @Value("${app.summary.gemini-api-key:}")
    private String geminiApiKey;

    @Value("${app.summary.gemini-model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${app.summary.gemini-url:https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}}")
    private String geminiUrl;

    @Value("${app.summary.max-output-tokens:1024}")
    private int maxOutputTokens;

    public ConversationSummaryResponse summarize(String conversationId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with ID: " + conversationId));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(member -> member.getId().equals(currentUser.getId()));
        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }
        rateLimitService.check("ai:summary", currentUser.getId(), summaryRateLimit,
                Duration.ofSeconds(summaryRateWindowSeconds));
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

        String summary = summarizeWithN8n(request);
        if (summary == null || summary.isBlank()) {
            summary = summarizeWithGemini(request);
        }
        if (summary == null || summary.isBlank()) {
            throw new BadRequestException("Không thể tạo bản tóm tắt lúc này. Vui lòng thử lại sau.");
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

    private String summarizeWithN8n(ConversationSummaryRequest request) {
        if (n8nWebhookUrl == null || n8nWebhookUrl.isBlank()) {
            return null;
        }
        try {
            ResponseEntity<Object> response = restTemplate.postForEntity(n8nWebhookUrl, request, Object.class);
            return response.getStatusCode().is2xxSuccessful() ? extractSummary(response.getBody()) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String summarizeWithGemini(ConversationSummaryRequest request) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return null;
        }

        StringBuilder transcript = new StringBuilder();
        for (SummaryMessagePayload message : request.getMessages()) {
            transcript.append(message.getSenderUsername())
                    .append(": ")
                    .append(message.getContent())
                    .append('\n');
        }
        String prompt = """
                Bạn là trợ lý tóm tắt hội thoại. Chỉ dựa trên nội dung được cung cấp, không suy diễn.
                Hãy trả lời bằng tiếng Việt có dấu, tối đa 4 dòng, gồm chủ đề chính, quyết định đã chốt
                và công việc được giao nếu có. Không thêm lời dẫn hoặc định dạng JSON.

                Hội thoại:
                %s
                """.formatted(transcript.toString().trim());

        Map<String, Object> payload = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", maxOutputTokens
                )
        );
        try {
            ResponseEntity<Object> response = restTemplate.postForEntity(
                    geminiUrl, payload, Object.class, geminiModel, geminiApiKey);
            return response.getStatusCode().is2xxSuccessful() ? extractGeminiText(response.getBody()) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractGeminiText(Object body) {
        if (!(body instanceof Map<?, ?> map)) return null;
        Object candidatesRaw = map.get("candidates");
        if (!(candidatesRaw instanceof List<?> candidates) || candidates.isEmpty()) return null;
        Object candidateRaw = candidates.get(0);
        if (!(candidateRaw instanceof Map<?, ?> candidate)) return null;
        Object contentRaw = candidate.get("content");
        if (!(contentRaw instanceof Map<?, ?> content)) return null;
        Object partsRaw = content.get("parts");
        if (!(partsRaw instanceof List<?> parts)) return null;
        return parts.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(part -> part.get("text"))
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .filter(text -> !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
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
