package iuh.fit.se.nextalk_be.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.entity.UserReport;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.dto.response.MessageResponse;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.MessageStatusRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.UserReportRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.ModerationAiService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModerationAiServiceImpl implements ModerationAiService {

    private static final Logger log = LoggerFactory.getLogger(ModerationAiServiceImpl.class);

    private final UserReportRepository userReportRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final MessageStatusRepository messageStatusRepository;
    private final RestTemplate restTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ai-bot.gemini-api-key:}")
    private String geminiApiKey;

    @Value("${app.ai-bot.gemini-model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${app.ai-bot.gemini-url}")
    private String geminiUrl;

    @Async
    @Override
    public void evaluateReportAsync(String reportId) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            log.warn("Gemini API key is missing. Skipping AI moderation for report {}", reportId);
            return;
        }

        UserReport report = userReportRepository.findById(reportId).orElse(null);
        if (report == null || !"PENDING".equals(report.getStatus())) {
            return;
        }

        try {
            List<Message> messages = List.of();
            if (report.getConversationId() != null) {
                messages = messageRepository.findVisibleConversationMessages(
                        report.getConversationId(),
                        report.getReporter().getId(),
                        PageRequest.of(0, 50)
                ).getContent();
            }

            String chatHistory = messages.stream()
                    .sorted((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()))
                    .map(m -> m.getSender().getUsername() + ": " + m.getContent())
                    .collect(Collectors.joining("\n"));

            String prompt = String.format(
                    "Bạn là một Quản trị viên (Moderator AI) của một ứng dụng chat.\n" +
                    "Người dùng '%s' đã báo cáo người dùng '%s'.\n" +
                    "Lý do báo cáo: %s\n" +
                    "Chi tiết: %s\n" +
                    "Lịch sử chat gần nhất (nếu có):\n%s\n\n" +
                    "Nhiệm vụ của bạn là đánh giá xem người bị báo cáo có vi phạm tiêu chuẩn cộng đồng (spam, quấy rối, lừa đảo, ngôn từ thù ghét) hay không. " +
                    "Trả về KẾT QUẢ THEO ĐỊNH DẠNG JSON với các trường:\n" +
                    "- \"violation\": true/false\n" +
                    "- \"severity\": \"HIGH\", \"MEDIUM\", \"LOW\", hoặc \"NONE\"\n" +
                    "- \"suggestedAction\": \"BAN\", \"WARN\", \"SAFE\", hoặc \"HUMAN_REVIEW\"\n" +
                    "- \"reasoning\": Lý do giải thích chi tiết bằng Tiếng Việt.",
                    report.getReporter().getUsername(),
                    report.getReportedUser().getUsername(),
                    report.getReason(),
                    report.getDescription() != null ? report.getDescription() : "Không có",
                    chatHistory.isBlank() ? "Không có lịch sử" : chatHistory
            );

            Map<String, Object> payload = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    )
            );

            ResponseEntity<Object> response = restTemplate.postForEntity(
                    geminiUrl,
                    payload,
                    Object.class,
                    geminiModel,
                    geminiApiKey
            );

            String json = extractGeminiText(response.getBody());
            if (json == null || json.isBlank()) {
                throw new IllegalStateException("Empty response from AI");
            }

            json = json.trim();
            if (json.startsWith("```")) {
                json = json.replaceFirst("(?s)^```(?:json)?\\s*", "").replaceFirst("(?s)\\s*```$", "");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> aiResult = objectMapper.readValue(json, Map.class);
            String action = String.valueOf(aiResult.get("suggestedAction")).toUpperCase();
            String reasoning = String.valueOf(aiResult.get("reasoning"));

            report.setAiVerdict(action);
            report.setAiReasoning(reasoning);
            report.setStatus("RESOLVED");

            if ("BAN".equals(action)) {
                User reported = report.getReportedUser();
                reported.setAccountLocked(true);
                userRepository.save(reported);
                
                sendSystemMessage(report.getReporter(), "Báo cáo của bạn đối với " + reported.getUsername() + " đã được xử lý. Kết quả: Khóa tài khoản do vi phạm tiêu chuẩn cộng đồng.");
                sendSystemMessage(reported, "Tài khoản của bạn đã bị khóa do vi phạm tiêu chuẩn cộng đồng. Lý do: " + reasoning);
                
                // Force logout notification
                Map<String, Object> notif = new HashMap<>();
                notif.put("type", "ACCOUNT_BANNED");
                notif.put("message", "Tài khoản của bạn đã bị khóa do vi phạm tiêu chuẩn cộng đồng. Lý do: " + reasoning);
                messagingTemplate.convertAndSendToUser(reported.getUsername(), "/queue/notifications", notif);
            } else if ("WARN".equals(action)) {
                sendSystemMessage(report.getReporter(), "Báo cáo của bạn đối với " + report.getReportedUser().getUsername() + " đã được xử lý. Kết quả: Cảnh cáo.");
                sendSystemMessage(report.getReportedUser(), "Cảnh báo từ hệ thống: " + reasoning);
            } else {
                sendSystemMessage(report.getReporter(), "Báo cáo của bạn đối với " + report.getReportedUser().getUsername() + " đã được xử lý. Kết quả: Hệ thống AI đã kiểm tra và không phát hiện vi phạm tiêu chuẩn cộng đồng.");
            }

            userReportRepository.save(report);

        } catch (Exception e) {
            log.error("Error evaluating report {}", reportId, e);
            report.setAiVerdict("ERROR");
            report.setAiReasoning("Lỗi hệ thống: " + e.getMessage());
            userReportRepository.save(report);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractGeminiText(Object responseBody) {
        try {
            Map<String, Object> map = (Map<String, Object>) responseBody;
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) map.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    return (String) parts.get(0).get("text");
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract Gemini text", e);
        }
        return null;
    }

    private User getSystemBot() {
        User bot = userRepository.findByEmail("moderator@nextalk.local").orElseGet(() -> {
            User newBot = new User();
            newBot.setUsername("NexTalk Moderator");
            newBot.setEmail("moderator@nextalk.local");
            newBot.setPassword("moderator_hidden_password");
            newBot.setAvatarUrl("https://res.cloudinary.com/dp5r0dqqh/image/upload/v1783700471/nextalk/nnjdwhw3tfhjjjymbfgj.png");
            newBot.setStatus("ONLINE");
            newBot.setVerified(true);
            return userRepository.save(newBot);
        });
        if (!"https://res.cloudinary.com/dp5r0dqqh/image/upload/v1783700471/nextalk/nnjdwhw3tfhjjjymbfgj.png".equals(bot.getAvatarUrl())) {
            bot.setAvatarUrl("https://res.cloudinary.com/dp5r0dqqh/image/upload/v1783700471/nextalk/nnjdwhw3tfhjjjymbfgj.png");
            bot = userRepository.save(bot);
        }
        return bot;
    }

    private Conversation getOrCreateBotConversation(User user) {
        User bot = getSystemBot();
        org.bson.types.ObjectId botId = new org.bson.types.ObjectId(bot.getId());
        org.bson.types.ObjectId userId = new org.bson.types.ObjectId(user.getId());

        return conversationRepository.findPrivateConversationBetweenUsers(botId, userId).orElseGet(() -> {
            Conversation newConv = new Conversation();
            newConv.setType(iuh.fit.se.nextalk_be.entity.ConversationType.PRIVATE);
            newConv.setMembers(java.util.Set.of(bot, user));
            newConv.setCreatedAt(LocalDateTime.now());
            newConv.setUpdatedAt(LocalDateTime.now());
            return conversationRepository.save(newConv);
        });
    }

    private void sendSystemMessage(User targetUser, String text) {
        if (targetUser == null) return;
        
        Conversation conversation = getOrCreateBotConversation(targetUser);
        User bot = getSystemBot();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("systemType", "AI_BOT_REPLY");
        metadata.put("botName", "NexTalk Moderator");

        Message botMessage = Message.builder()
                .conversation(conversation)
                .sender(bot)
                .content(text)
                .messageType(MessageType.SYSTEM)
                .metadata(metadata)
                .build();

        Message savedMessage = messageRepository.save(botMessage);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        MessageResponse response = MessageResponse.builder()
                .id(savedMessage.getId())
                .conversationId(conversation.getId())
                .senderId(bot.getId())
                .senderUsername(bot.getUsername())
                .content(text)
                .messageType(MessageType.SYSTEM.name())
                .attachments(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .statuses(messageStatusRepository.findAllByMessageId(savedMessage.getId()).stream().map(status ->
                        iuh.fit.se.nextalk_be.dto.response.MessageStatusResponse.builder()
                                .userId(status.getUser().getId())
                                .username(status.getUser().getUsername())
                                .status(status.getStatus())
                                .updatedAt(status.getUpdatedAt() != null ? status.getUpdatedAt() : status.getCreatedAt())
                                .build()
                ).toList())
                .metadata(metadata)
                .build();

        for (User member : conversation.getMembers()) {
            messagingTemplate.convertAndSendToUser(member.getUsername(), "/queue/private", response);
        }
    }
}
