package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.SummaryMessagePayload;
import iuh.fit.se.nextalk_be.dto.request.AiBotRequest;
import iuh.fit.se.nextalk_be.dto.response.MessageResponse;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.MessageStatusRepository;
import iuh.fit.se.nextalk_be.service.AiBotService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiBotServiceImpl implements AiBotService {
    private static final Pattern REMINDER_INTENT_PATTERN = Pattern.compile(
            "(?iu)(nhac|nhắc|nho|nhớ|remind|reminder|hen|hẹn|lich|lịch)"
    );
    private static final Pattern RELATIVE_TIME_PATTERN = Pattern.compile(
            "(?iu)(?:sau|trong)\\s+(\\d{1,3})\\s*(phut|phút|p|minute|minutes|min|gio|giờ|h|hour|hours|ngay|ngày|day|days)"
    );
    private static final Pattern HOUR_PATTERN = Pattern.compile(
            "(?iu)(?:luc|lúc|vao|vào|at)?\\s*(\\d{1,2})(?:[:h]\\s*(\\d{1,2}))?\\s*(sang|sáng|chieu|chiều|toi|tối|dem|đêm|am|pm)?"
    );
    private static final DateTimeFormatter REMINDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final MessageStatusRepository messageStatusRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RestTemplate restTemplate;

    @Value("${app.ai-bot.n8n-webhook-url:}")
    private String aiBotWebhookUrl;

    @Value("${app.ai-bot.message-limit:12}")
    private int messageLimit;

    @Value("${app.summary.preferred-model:gemini-2.5-flash}")
    private String preferredModel;

    @Value("${app.ai-bot.gemini-api-key:}")
    private String geminiApiKey;

    @Value("${app.ai-bot.gemini-model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${app.ai-bot.gemini-url}")
    private String geminiUrl;

    @Value("${app.ai-bot.max-output-tokens:2048}")
    private int maxOutputTokens;

    @Async
    @Override
    public void answerMentionAsync(Conversation conversation, Message triggerMessage, User requester) {
        String answer;
        Map<String, Object> metadata = Map.of();
        try {
            AiBotReply reply = requestAnswer(conversation, triggerMessage, requester);
            answer = reply.answer();
            metadata = reply.metadata();
        } catch (Exception e) {
            answer = "Minh chua the tra loi ngay luc nay. Vui long thu lai sau.";
        }

        createAndBroadcastBotMessage(conversation, requester, answer, triggerMessage.getId(), metadata);
    }

    private AiBotReply requestAnswer(Conversation conversation, Message triggerMessage, User requester) {
        String question = extractQuestion(triggerMessage);
        Optional<ReminderIntent> reminderIntent = parseReminderIntent(question);
        if (reminderIntent.isPresent()) {
            ReminderIntent reminder = reminderIntent.get();
            if (reminder.remindAt() == null) {
                return new AiBotReply(
                        "Ban muon minh nhac vao thoi gian nao? Vi du: \"@bot nhac toi gui bao cao luc 9h sang mai\".",
                        Map.of()
                );
            }

            String note = reminder.note().isBlank() ? "Nhac hen tu NexTalk AI" : reminder.note();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("systemType", "AI_REMINDER_CREATE");
            metadata.put("action", "CREATE_MESSAGE_REMINDER");
            metadata.put("messageId", triggerMessage.getId());
            metadata.put("remindAt", reminder.remindAt().atZone(ZoneId.systemDefault()).toInstant().toString());
            metadata.put("note", note);
            metadata.put("preview", stripHtml(triggerMessage.getContent()).trim());
            metadata.put("requestedByUserId", requester.getId());

            String formattedTime = reminder.remindAt().format(REMINDER_TIME_FORMATTER);
            return new AiBotReply("Minh da tao nhac hen cho ban luc " + formattedTime + ": " + note, metadata);
        }

        if ((geminiApiKey == null || geminiApiKey.isBlank()) && (aiBotWebhookUrl == null || aiBotWebhookUrl.isBlank())) {
            return new AiBotReply("AI Bot chua duoc cau hinh webhook.", Map.of());
        }

        List<SummaryMessagePayload> contextMessages = messageRepository
                .findByConversationIdOrderByCreatedAtDesc(conversation.getId(), PageRequest.of(0, messageLimit))
                .getContent()
                .stream()
                .filter(message -> isReadableText(message) && !message.getId().equals(triggerMessage.getId()))
                .sorted(Comparator.comparing(Message::getCreatedAt))
                .map(message -> SummaryMessagePayload.builder()
                        .senderId(message.getSender().getId())
                        .senderUsername(message.getSender().getUsername())
                        .content(stripHtml(message.getContent()).trim())
                        .createdAt(message.getCreatedAt())
                        .build())
                .toList();

        AiBotRequest request = AiBotRequest.builder()
                .conversationId(conversation.getId())
                .conversationName(conversation.getName())
                .conversationType(conversation.getType().name())
                .requestedByUserId(requester.getId())
                .requestedByUsername(requester.getUsername())
                .question(question)
                .triggerMessageId(triggerMessage.getId())
                .messageCount(contextMessages.size())
                .preferredModel(preferredModel)
                .instruction("Ban la NexTalk Bot trong group chat. Tra loi truc tiep cau hoi cua nguoi dung bang tieng Viet, ngan gon, huu ich, lich su. Neu can, dung ngu canh tin nhan gan nhat nhung khong bia thong tin.")
                .messages(contextMessages)
                .build();

        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            String geminiAnswer = requestGeminiAnswer(request);
            if (geminiAnswer != null && !geminiAnswer.isBlank()) {
                return new AiBotReply(geminiAnswer.trim(), Map.of());
            }
        }

        if (aiBotWebhookUrl == null || aiBotWebhookUrl.isBlank()) {
            return new AiBotReply("NexTalk AI chua nhan duoc cau tra loi phu hop tu Gemini.", Map.of());
        }

        ResponseEntity<Object> response = restTemplate.postForEntity(aiBotWebhookUrl, request, Object.class);
        String answer = extractAnswer(response.getBody());
        if (answer == null || answer.isBlank()) {
            return new AiBotReply("Minh chua nhan duoc cau tra loi phu hop tu AI.", Map.of());
        }
        return new AiBotReply(answer.trim(), Map.of());
    }

    private String requestGeminiAnswer(AiBotRequest request) {
        Map<String, Object> payload = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", buildPrompt(request)))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.5,
                        "maxOutputTokens", maxOutputTokens
                )
        );

        ResponseEntity<Object> response = restTemplate.postForEntity(
                geminiUrl,
                payload,
                Object.class,
                geminiModel,
                geminiApiKey
        );
        return extractGeminiText(response.getBody());
    }

    private String buildPrompt(AiBotRequest request) {
        StringBuilder contextBuilder = new StringBuilder();
        if (request.getMessages() != null) {
            for (SummaryMessagePayload message : request.getMessages()) {
                contextBuilder
                        .append(message.getSenderUsername())
                        .append(": ")
                        .append(message.getContent())
                        .append('\n');
            }
        }

        return """
                Ban la NexTalk AI trong group chat.
                Nguoi hoi: %s
                Cau hoi: %s

                Ngu canh gan nhat:
                %s

                Hay tra loi bang tieng Viet, ro rang, huu ich va phai ket thuc tron y.
                Neu cau hoi can giai thich, hay trinh bay ngan gon theo cac muc: khai niem, dac diem chinh, vi du/ung dung.
                Khong dung o giua cau hoac giua danh sach. Neu khong du du kien, hay noi ro ban can them thong tin.
                """.formatted(
                request.getRequestedByUsername(),
                request.getQuestion(),
                contextBuilder.length() == 0 ? "(Khong co ngu canh)" : contextBuilder.toString().trim()
        );
    }

    private String extractGeminiText(Object body) {
        if (!(body instanceof Map<?, ?> map)) {
            return body != null ? String.valueOf(body) : null;
        }
        Object candidatesRaw = map.get("candidates");
        if (!(candidatesRaw instanceof List<?> candidates) || candidates.isEmpty()) {
            return null;
        }
        StringBuilder answer = new StringBuilder();
        boolean reachedTokenLimit = false;

        for (Object candidateRaw : candidates) {
            if (!(candidateRaw instanceof Map<?, ?> candidate)) {
                continue;
            }

            Object finishReason = candidate.get("finishReason");
            if ("MAX_TOKENS".equals(String.valueOf(finishReason))) {
                reachedTokenLimit = true;
            }

            Object contentRaw = candidate.get("content");
            if (!(contentRaw instanceof Map<?, ?> content)) {
                continue;
            }

            Object partsRaw = content.get("parts");
            if (!(partsRaw instanceof List<?> parts)) {
                continue;
            }

            for (Object partRaw : parts) {
                if (!(partRaw instanceof Map<?, ?> part)) {
                    continue;
                }
                Object text = part.get("text");
                if (text != null && !String.valueOf(text).isBlank()) {
                    if (answer.length() > 0) {
                        answer.append('\n');
                    }
                    answer.append(String.valueOf(text).trim());
                }
            }
        }

        if (answer.length() == 0) {
            return null;
        }
        if (reachedTokenLimit) {
            answer.append("\n\n(Cau tra loi dang dai nen minh tam dung tai day. Ban co the hoi tiep de minh giai thich phan con lai.)");
        }
        return answer.toString().trim();
    }

    private boolean isReadableText(Message message) {
        return message != null
                && !message.isRecalled()
                && message.getMessageType() == MessageType.TEXT
                && message.getContent() != null
                && !stripHtml(message.getContent()).isBlank();
    }

    private String stripHtml(String value) {
        if (value == null) return "";
        return value
                .replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String extractAnswer(Object body) {
        if (body == null) return null;
        if (body instanceof String text) return text;
        if (!(body instanceof Map<?, ?> map)) return String.valueOf(body);

        Object answer = map.get("answer");
        if (answer == null) answer = map.get("reply");
        if (answer == null) answer = map.get("message");
        if (answer == null) answer = map.get("summary");
        if (answer == null) answer = map.get("text");
        if (answer == null) answer = map.get("output");
        if (answer == null) answer = map.get("result");
        return answer != null ? String.valueOf(answer) : null;
    }

    private String extractQuestion(Message triggerMessage) {
        String question = stripHtml(triggerMessage.getContent())
                .replaceAll("(?i)(^|\\s)@(bot|nextalk\\s+ai|meta\\s+ai)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (question.isBlank()) {
            question = "Hay ho tro cuoc tro chuyen nay dua tren ngu canh gan nhat.";
        }
        return question;
    }

    private Optional<ReminderIntent> parseReminderIntent(String question) {
        if (question == null || question.isBlank() || !REMINDER_INTENT_PATTERN.matcher(question).find()) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime remindAt = parseRelativeReminderTime(question, now)
                .orElseGet(() -> parseAbsoluteReminderTime(question, now).orElse(null));
        return Optional.of(new ReminderIntent(cleanReminderNote(question), remindAt));
    }

    private Optional<LocalDateTime> parseRelativeReminderTime(String question, LocalDateTime now) {
        Matcher matcher = RELATIVE_TIME_PATTERN.matcher(question);
        if (!matcher.find()) {
            return Optional.empty();
        }

        long amount = Long.parseLong(matcher.group(1));
        String unit = normalizeText(matcher.group(2));
        Duration duration;
        if (unit.startsWith("phut") || unit.equals("p") || unit.startsWith("min")) {
            duration = Duration.ofMinutes(amount);
        } else if (unit.startsWith("gio") || unit.equals("h") || unit.startsWith("hour")) {
            duration = Duration.ofHours(amount);
        } else {
            duration = Duration.ofDays(amount);
        }
        return Optional.of(now.plus(duration));
    }

    private Optional<LocalDateTime> parseAbsoluteReminderTime(String question, LocalDateTime now) {
        Matcher matcher = HOUR_PATTERN.matcher(question);
        LocalTime time = null;
        while (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
            if (hour > 23 || minute > 59) {
                continue;
            }
            String partOfDay = matcher.group(3);
            if (partOfDay != null) {
                String normalizedPart = normalizeText(partOfDay);
                if ((normalizedPart.equals("chieu") || normalizedPart.equals("toi") || normalizedPart.equals("dem") || normalizedPart.equals("pm")) && hour < 12) {
                    hour += 12;
                } else if ((normalizedPart.equals("sang") || normalizedPart.equals("am")) && hour == 12) {
                    hour = 0;
                }
            }
            time = LocalTime.of(hour, minute);
        }

        String normalized = normalizeText(question);
        LocalDateTime base;
        if (normalized.contains("ngay kia")) {
            base = now.plusDays(2);
        } else if (normalized.contains("mai") || normalized.contains("tomorrow")) {
            base = now.plusDays(1);
        } else if (normalized.contains("toi nay")) {
            base = now;
            if (time == null) {
                time = LocalTime.of(20, 0);
            }
        } else {
            base = applyWeekday(normalized, now).orElse(now);
        }

        if (time == null) {
            return Optional.empty();
        }

        LocalDateTime remindAt = LocalDateTime.of(base.toLocalDate(), time);
        if (!normalized.contains("mai") && !normalized.contains("ngay kia") && !normalized.contains("tomorrow")
                && !containsWeekday(normalized) && !remindAt.isAfter(now)) {
            remindAt = remindAt.plusDays(1);
        }
        return Optional.of(remindAt);
    }

    private Optional<LocalDateTime> applyWeekday(String normalized, LocalDateTime now) {
        Map<String, DayOfWeek> weekdays = Map.ofEntries(
                Map.entry("thu 2", DayOfWeek.MONDAY),
                Map.entry("thu hai", DayOfWeek.MONDAY),
                Map.entry("thu 3", DayOfWeek.TUESDAY),
                Map.entry("thu ba", DayOfWeek.TUESDAY),
                Map.entry("thu 4", DayOfWeek.WEDNESDAY),
                Map.entry("thu tu", DayOfWeek.WEDNESDAY),
                Map.entry("thu 5", DayOfWeek.THURSDAY),
                Map.entry("thu nam", DayOfWeek.THURSDAY),
                Map.entry("thu 6", DayOfWeek.FRIDAY),
                Map.entry("thu sau", DayOfWeek.FRIDAY),
                Map.entry("thu 7", DayOfWeek.SATURDAY)
        );
        for (Map.Entry<String, DayOfWeek> entry : weekdays.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                int days = entry.getValue().getValue() - now.getDayOfWeek().getValue();
                if (days <= 0) {
                    days += 7;
                }
                return Optional.of(now.plusDays(days));
            }
        }
        if (normalized.contains("chu nhat") || normalized.contains("cn")) {
            int days = DayOfWeek.SUNDAY.getValue() - now.getDayOfWeek().getValue();
            if (days <= 0) {
                days += 7;
            }
            return Optional.of(now.plusDays(days));
        }
        return Optional.empty();
    }

    private boolean containsWeekday(String normalized) {
        return normalized.contains("thu ") || normalized.contains("chu nhat") || normalized.contains("cn");
    }

    private String cleanReminderNote(String question) {
        return question
                .replaceAll("(?iu)\\b(nhac|nhắc|nho|nhớ|remind|reminder)\\b", " ")
                .replaceAll("(?iu)\\b(toi|tôi|minh|mình|em|anh|chi|chị)\\b", " ")
                .replaceAll("(?iu)(?:sau|trong)\\s+\\d{1,3}\\s*(phut|phút|p|minute|minutes|min|gio|giờ|h|hour|hours|ngay|ngày|day|days)", " ")
                .replaceAll("(?iu)\\b(luc|lúc|vao|vào|at)\\s*\\d{1,2}(?:[:h]\\s*\\d{1,2})?\\s*(sang|sáng|chieu|chiều|toi|tối|dem|đêm|am|pm)?", " ")
                .replaceAll("(?iu)\\b(hom nay|hôm nay|toi nay|tối nay|ngay mai|ngày mai|mai|ngay kia|ngày kia|tomorrow)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeText(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase();
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private void createAndBroadcastBotMessage(Conversation conversation, User requester, String answer, String triggerMessageId, Map<String, Object> extraMetadata) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("systemType", "AI_BOT_REPLY");
        metadata.put("botName", "NexTalk AI");
        metadata.put("triggerMessageId", triggerMessageId);
        if (extraMetadata != null) {
            metadata.putAll(extraMetadata);
        }

        Message botMessage = Message.builder()
                .conversation(conversation)
                .sender(requester)
                .content(answer)
                .messageType(MessageType.SYSTEM)
                .parentId(triggerMessageId)
                .metadata(metadata)
                .build();

        Message savedMessage = messageRepository.save(botMessage);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        MessageResponse response = mapBotMessageResponse(savedMessage);
        for (User member : conversation.getMembers()) {
            messagingTemplate.convertAndSendToUser(member.getUsername(), "/queue/private", response);
        }
    }

    private MessageResponse mapBotMessageResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderId(message.getSender().getId())
                .senderUsername(message.getSender().getUsername())
                .content(message.getContent())
                .messageType(message.getMessageType().name())
                .attachments(message.getAttachments() != null ? message.getAttachments() : new ArrayList<>())
                .createdAt(message.getCreatedAt() != null ? message.getCreatedAt() : LocalDateTime.now())
                .statuses(messageStatusRepository.findAllByMessageId(message.getId()).stream().map(status ->
                        iuh.fit.se.nextalk_be.dto.response.MessageStatusResponse.builder()
                                .userId(status.getUser().getId())
                                .username(status.getUser().getUsername())
                                .status(status.getStatus())
                                .updatedAt(status.getUpdatedAt() != null ? status.getUpdatedAt() : status.getCreatedAt())
                                .build()
                ).toList())
                .parentId(message.getParentId())
                .isEdited(message.isEdited())
                .editedAt(message.getEditedAt())
                .isRecalled(message.isRecalled())
                .isPinned(message.isPinned())
                .pinnedAt(message.getPinnedAt())
                .expiresAt(message.getExpiresAt())
                .reactions(message.getReactions() != null ? message.getReactions() : new ArrayList<>())
                .metadata(message.getMetadata() != null ? message.getMetadata() : Map.of())
                .build();
    }

    private record AiBotReply(String answer, Map<String, Object> metadata) {
    }

    private record ReminderIntent(String note, LocalDateTime remindAt) {
    }
}
