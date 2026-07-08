package iuh.fit.se.nextalk_be.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.nextalk_be.dto.SummaryMessagePayload;
import iuh.fit.se.nextalk_be.dto.request.AiBotRequest;
import iuh.fit.se.nextalk_be.dto.request.CreateMessageReminderRequest;
import iuh.fit.se.nextalk_be.dto.request.CreatePollRequest;
import iuh.fit.se.nextalk_be.dto.response.ConversationSummaryResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageReminderResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageResponse;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.MessageStatusRepository;
import iuh.fit.se.nextalk_be.service.AiBotService;
import iuh.fit.se.nextalk_be.service.ConversationSummaryService;
import iuh.fit.se.nextalk_be.service.MessageReminderService;
import iuh.fit.se.nextalk_be.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private static final Pattern POLL_INTENT_PATTERN = Pattern.compile("(?iu)(poll|binh chon|bình chọn|bo phieu|bỏ phiếu)");
    private static final Pattern SEARCH_INTENT_PATTERN = Pattern.compile("(?iu)(tim|tìm|search|kiem|kiếm)");
    private static final Pattern SUMMARY_INTENT_PATTERN = Pattern.compile("(?iu)(tom tat|tóm tắt|summary|tong ket|tổng kết)");
    private static final DateTimeFormatter REMINDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final MessageStatusRepository messageStatusRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RestTemplate restTemplate;
    private final MessageReminderService messageReminderService;
    private final ConversationSummaryService conversationSummaryService;
    private final ObjectProvider<MessageService> messageServiceProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            answer = "Mình chưa thể thực hiện yêu cầu này ngay lúc này. Bạn thử lại sau nhé.";
        }

        createAndBroadcastBotMessage(conversation, requester, answer, triggerMessage.getId(), metadata);
    }

    private AiBotReply requestAnswer(Conversation conversation, Message triggerMessage, User requester) {
        String question = extractQuestion(triggerMessage);
        List<SummaryMessagePayload> contextMessages = getContextMessages(conversation, triggerMessage);
        AiBotRequest request = buildAiRequest(conversation, triggerMessage, requester, question, contextMessages);

        AiAction action = requestStructuredAction(request);
        if (action != null && action.intent() != null) {
            AiBotReply actionReply = executeAction(action, conversation, triggerMessage, requester, question);
            if (actionReply != null) {
                return actionReply;
            }
        }

        AiBotReply heuristicReply = executeHeuristicAction(question, conversation, triggerMessage, requester);
        if (heuristicReply != null) {
            return heuristicReply;
        }

        if ((geminiApiKey == null || geminiApiKey.isBlank()) && (aiBotWebhookUrl == null || aiBotWebhookUrl.isBlank())) {
            return new AiBotReply("AI Bot chưa được cấu hình.", Map.of());
        }

        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            String geminiAnswer = requestGeminiAnswer(request);
            if (geminiAnswer != null && !geminiAnswer.isBlank()) {
                return new AiBotReply(geminiAnswer.trim(), Map.of());
            }
        }

        if (aiBotWebhookUrl == null || aiBotWebhookUrl.isBlank()) {
            return new AiBotReply("NexTalk AI chưa nhận được câu trả lời phù hợp.", Map.of());
        }

        ResponseEntity<Object> response = restTemplate.postForEntity(aiBotWebhookUrl, request, Object.class);
        String answer = extractAnswer(response.getBody());
        if (answer == null || answer.isBlank()) {
            return new AiBotReply("Mình chưa nhận được câu trả lời phù hợp từ AI.", Map.of());
        }
        return new AiBotReply(answer.trim(), Map.of());
    }

    private AiAction requestStructuredAction(AiBotRequest request) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return null;
        }

        Map<String, Object> payload = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", buildRouterPrompt(request)))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.1,
                        "maxOutputTokens", Math.min(maxOutputTokens, 1400),
                        "responseMimeType", "application/json"
                )
        );

        try {
            ResponseEntity<Object> response = restTemplate.postForEntity(
                    geminiUrl,
                    payload,
                    Object.class,
                    geminiModel,
                    geminiApiKey
            );
            String json = extractGeminiText(response.getBody());
            if (json == null || json.isBlank()) {
                return null;
            }
            return parseAiAction(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private AiAction parseAiAction(String rawJson) throws Exception {
        String json = rawJson.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("(?s)^```(?:json)?\\s*", "").replaceFirst("(?s)\\s*```$", "");
        }
        Map<String, Object> map = objectMapper.readValue(json, Map.class);
        String intent = stringValue(map.get("intent")).toUpperCase();
        String reply = stringValue(map.get("reply"));
        Object paramsRaw = map.get("parameters");
        Map<String, Object> parameters = paramsRaw instanceof Map<?, ?> rawMap
                ? new HashMap<>((Map<String, Object>) rawMap)
                : new HashMap<>();
        return new AiAction(intent, parameters, reply);
    }

    private AiBotReply executeAction(AiAction action, Conversation conversation, Message triggerMessage, User requester, String question) {
        return runAs(requester, () -> switch (action.intent()) {
            case "ANSWER" -> action.reply().isBlank() ? null : new AiBotReply(action.reply(), Map.of());
            case "ASK_CLARIFICATION" -> new AiBotReply(
                    action.reply().isBlank() ? "Bạn cho mình thêm thông tin để thực hiện nhé." : action.reply(),
                    Map.of()
            );
            case "CREATE_REMINDER" -> createReminderAction(action, triggerMessage);
            case "CREATE_POLL" -> createPollAction(action, conversation);
            case "SEARCH_MESSAGES" -> searchMessagesAction(action, conversation, question);
            case "SUMMARIZE_CONVERSATION" -> summarizeConversationAction(conversation);
            default -> null;
        });
    }

    private AiBotReply executeHeuristicAction(String question, Conversation conversation, Message triggerMessage, User requester) {
        String normalized = normalizeText(question);
        if (SUMMARY_INTENT_PATTERN.matcher(question).find()) {
            return runAs(requester, () -> summarizeConversationAction(conversation));
        }
        if (SEARCH_INTENT_PATTERN.matcher(question).find()) {
            String query = normalized
                    .replaceAll("\\b(tim|search|kiem|tin nhan|message|file)\\b", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (query.length() >= 2) {
                return runAs(requester, () -> searchMessagesAction(new AiAction("SEARCH_MESSAGES", Map.of("query", query), ""), conversation, question));
            }
        }
        if (POLL_INTENT_PATTERN.matcher(question).find()) {
            AiAction pollAction = parseSimplePoll(question);
            if (pollAction != null) {
                return runAs(requester, () -> createPollAction(pollAction, conversation));
            }
        }

        Optional<ReminderIntent> reminderIntent = parseReminderIntent(question);
        if (reminderIntent.isEmpty()) {
            return null;
        }
        ReminderIntent reminder = reminderIntent.get();
        if (reminder.remindAt() == null) {
            return new AiBotReply("Bạn muốn mình nhắc vào thời gian nào?", Map.of());
        }
        return runAs(requester, () -> {
            MessageReminderResponse response = messageReminderService.createReminder(CreateMessageReminderRequest.builder()
                    .messageId(triggerMessage.getId())
                    .remindAt(reminder.remindAt().atZone(ZoneId.systemDefault()).toInstant().toString())
                    .note(reminder.note().isBlank() ? "Nhắc hẹn từ NexTalk AI" : reminder.note())
                    .build());
            return new AiBotReply("Mình đã tạo nhắc hẹn lúc " + formatReminderTime(response.getRemindAt()) + ": " + response.getNote(), Map.of());
        });
    }

    private AiBotReply createReminderAction(AiAction action, Message triggerMessage) {
        String remindAt = stringParam(action, "remindAt");
        String note = stringParam(action, "note");
        if (remindAt.isBlank()) {
            return new AiBotReply("Bạn muốn mình nhắc vào thời gian nào?", Map.of());
        }
        MessageReminderResponse response = messageReminderService.createReminder(CreateMessageReminderRequest.builder()
                .messageId(triggerMessage.getId())
                .remindAt(remindAt)
                .note(note.isBlank() ? "Nhắc hẹn từ NexTalk AI" : note)
                .build());
        String reply = action.reply().isBlank()
                ? "Mình đã tạo nhắc hẹn lúc " + formatReminderTime(response.getRemindAt()) + ": " + response.getNote()
                : action.reply();
        return new AiBotReply(reply, Map.of());
    }

    private AiBotReply createPollAction(AiAction action, Conversation conversation) {
        String question = stringParam(action, "question");
        List<String> options = stringListParam(action, "options");
        if (question.isBlank() || options.size() < 2) {
            return new AiBotReply("Bạn cho mình câu hỏi và ít nhất 2 lựa chọn để tạo bình chọn nhé.", Map.of());
        }

        MessageResponse poll = messageServiceProvider.getObject().createPoll(CreatePollRequest.builder()
                .conversationId(conversation.getId())
                .question(question)
                .options(options)
                .allowMultiple(booleanParam(action, "allowMultiple"))
                .allowAddOptions(booleanParam(action, "allowAddOptions"))
                .anonymous(booleanParam(action, "anonymous"))
                .expiresAt(stringParam(action, "expiresAt"))
                .build());

        String reply = action.reply().isBlank()
                ? "Mình đã tạo bình chọn: " + poll.getContent()
                : action.reply();
        return new AiBotReply(reply, Map.of("action", "CREATE_POLL", "pollMessageId", poll.getId()));
    }

    private AiBotReply searchMessagesAction(AiAction action, Conversation conversation, String originalQuestion) {
        String query = stringParam(action, "query");
        if (query.isBlank()) {
            query = originalQuestion;
        }
        query = query.replaceAll("(?i)@?(bot|nextalk\\s+ai|meta\\s+ai)", " ").replaceAll("\\s+", " ").trim();
        if (query.length() < 2) {
            return new AiBotReply("Bạn muốn tìm nội dung gì?", Map.of());
        }

        List<MessageResponse> results = messageServiceProvider.getObject().searchMessages(query, conversation.getId());
        if (results.isEmpty()) {
            return new AiBotReply("Mình không tìm thấy tin nhắn nào phù hợp với: " + query, Map.of());
        }

        StringBuilder builder = new StringBuilder("Mình tìm thấy ").append(results.size()).append(" tin nhắn. Gần nhất:\n");
        results.stream().limit(5).forEach(message -> builder
                .append("- ")
                .append(message.getSenderUsername())
                .append(": ")
                .append(shorten(stripHtml(message.getContent()), 90))
                .append('\n'));
        return new AiBotReply(builder.toString().trim(), Map.of("action", "SEARCH_MESSAGES", "query", query));
    }

    private AiBotReply summarizeConversationAction(Conversation conversation) {
        ConversationSummaryResponse summary = conversationSummaryService.summarize(conversation.getId());
        return new AiBotReply("Tóm tắt gần đây:\n" + summary.getSummary(), Map.of("action", "SUMMARIZE_CONVERSATION"));
    }

    private AiAction parseSimplePoll(String question) {
        String cleaned = question
                .replaceAll("(?iu)@?(bot|nextalk\\s+ai|meta\\s+ai)", " ")
                .replaceAll("(?iu)(tao|tạo|lap|lập)?\\s*(poll|binh chon|bình chọn|bo phieu|bỏ phiếu)", " ")
                .replaceAll("\\s+", " ")
                .trim();
        String[] parts = cleaned.split("[:：]", 2);
        if (parts.length < 2) {
            parts = cleaned.split("(?iu)\\b(gom|gồm|cac lua chon|các lựa chọn|lua chon|lựa chọn)\\b", 2);
        }
        if (parts.length < 2) {
            return null;
        }
        List<String> options = List.of(parts[1].split("\\s*(?:,|/|\\||;| hoac | hoặc | hay )\\s*")).stream()
                .map(String::trim)
                .filter(option -> !option.isBlank())
                .distinct()
                .toList();
        if (options.size() < 2) {
            return null;
        }
        return new AiAction("CREATE_POLL", Map.of("question", parts[0].trim(), "options", options), "");
    }

    private <T> T runAs(User requester, ActionSupplier<T> supplier) {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    requester,
                    null,
                    requester.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return supplier.get();
        } catch (Exception e) {
            return (T) new AiBotReply(cleanActionError(e), Map.of());
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    private String cleanActionError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "Mình chưa thực hiện được yêu cầu này.";
        }
        return "Mình chưa thực hiện được: " + message;
    }

    private List<SummaryMessagePayload> getContextMessages(Conversation conversation, Message triggerMessage) {
        return messageRepository
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
    }

    private AiBotRequest buildAiRequest(
            Conversation conversation,
            Message triggerMessage,
            User requester,
            String question,
            List<SummaryMessagePayload> contextMessages
    ) {
        return AiBotRequest.builder()
                .conversationId(conversation.getId())
                .conversationName(conversation.getName())
                .conversationType(conversation.getType().name())
                .requestedByUserId(requester.getId())
                .requestedByUsername(requester.getUsername())
                .question(question)
                .triggerMessageId(triggerMessage.getId())
                .messageCount(contextMessages.size())
                .preferredModel(preferredModel)
                .instruction("Route user request to a safe NexTalk action or answer directly.")
                .messages(contextMessages)
                .build();
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

    private String buildRouterPrompt(AiBotRequest request) {
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
                You are NexTalk AI action router. Return ONLY valid JSON.
                Current date-time timezone: Asia/Ho_Chi_Minh. User: %s.
                User request: %s

                Recent context:
                %s

                Choose one intent:
                ANSWER, ASK_CLARIFICATION, CREATE_REMINDER, CREATE_POLL, SEARCH_MESSAGES, SUMMARIZE_CONVERSATION.

                JSON shape:
                {
                  "intent": "...",
                  "reply": "short Vietnamese user-facing reply with proper Vietnamese accents, or empty if backend should compose",
                  "parameters": {}
                }

                Parameter rules:
                CREATE_REMINDER: parameters.note, parameters.remindAt ISO-8601 with timezone. If time missing use ASK_CLARIFICATION.
                CREATE_POLL: parameters.question, parameters.options array with at least 2 items, optional allowMultiple, allowAddOptions, anonymous, expiresAt ISO string.
                SEARCH_MESSAGES: parameters.query.
                SUMMARIZE_CONVERSATION: parameters may be empty.
                ANSWER: reply must answer naturally in Vietnamese with proper accents.
                Do not invent permissions or private data. If unclear, use ASK_CLARIFICATION.
                """.formatted(
                request.getRequestedByUsername(),
                request.getQuestion(),
                contextBuilder.length() == 0 ? "(No context)" : contextBuilder.toString().trim()
        );
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
                Bạn là NexTalk AI trong group chat.
                Người hỏi: %s
                Câu hỏi: %s

                Ngữ cảnh gần nhất:
                %s

                Hãy trả lời bằng tiếng Việt có dấu, rõ ràng, hữu ích và phải kết thúc trọn ý.
                Nếu câu hỏi cần giải thích, hãy trình bày ngắn gọn theo các mục: khái niệm, đặc điểm chính, ví dụ/ứng dụng.
                Nếu không đủ dữ kiện, hãy nói rõ bạn cần thêm thông tin.
                """.formatted(
                request.getRequestedByUsername(),
                request.getQuestion(),
                contextBuilder.length() == 0 ? "(Không có ngữ cảnh)" : contextBuilder.toString().trim()
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
            answer.append("\n\n(Câu trả lời đang dài nên mình tạm dừng tại đây. Bạn có thể hỏi tiếp để mình giải thích phần còn lại.)");
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
            question = "Hãy hỗ trợ cuộc trò chuyện này dựa trên ngữ cảnh gần nhất.";
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

    private String stringParam(AiAction action, String key) {
        return stringValue(action.parameters().get(key));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean booleanParam(AiAction action, String key) {
        Object value = action.parameters().get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private List<String> stringListParam(AiAction action, String key) {
        Object raw = action.parameters().get(key);
        if (!(raw instanceof List<?> rawList)) {
            return List.of();
        }
        return rawList.stream()
                .map(this::stringValue)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String formatReminderTime(String instantString) {
        try {
            return java.time.Instant.parse(instantString)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                    .format(REMINDER_TIME_FORMATTER);
        } catch (Exception ignored) {
            return instantString;
        }
    }

    private String shorten(String value, int maxLength) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 3) + "...";
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

    @FunctionalInterface
    private interface ActionSupplier<T> {
        T get();
    }

    private record AiBotReply(String answer, Map<String, Object> metadata) {
    }

    private record AiAction(String intent, Map<String, Object> parameters, String reply) {
    }

    private record ReminderIntent(String note, LocalDateTime remindAt) {
    }
}
