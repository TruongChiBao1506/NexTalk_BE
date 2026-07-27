package iuh.fit.se.nextalk_be.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.nextalk_be.dto.response.BirthdayContextResponse;
import iuh.fit.se.nextalk_be.dto.response.ReplySuggestionsResponse;
import iuh.fit.se.nextalk_be.entity.BirthdayVisibility;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.FriendshipStatus;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.FriendshipRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.service.ConversationAssistService;
import iuh.fit.se.nextalk_be.service.UserService;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ConversationAssistServiceImpl implements ConversationAssistService {

    private static final Logger log = LoggerFactory.getLogger(ConversationAssistServiceImpl.class);
    private static final List<String> DEFAULT_BIRTHDAY_WISHES = List.of(
            "Chúc bạn sinh nhật vui vẻ, luôn hạnh phúc và gặp nhiều may mắn nhé! 🎂",
            "Chúc mừng sinh nhật! Chúc bạn có một ngày thật tuyệt vời 🎉",
            "Tuổi mới thật nhiều niềm vui, sức khỏe và thành công nhé! 🎈"
    );

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserService userService;
    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ai-reply.gemini-api-key:}")
    private String geminiApiKey;

    @Value("${app.ai-reply.gemini-model:gemini-3.6-flash}")
    private String geminiModel;

    @Value("${app.ai-reply.gemini-url}")
    private String geminiUrl;

    @Value("${app.ai-reply.message-limit:10}")
    private int messageLimit;

    @Value("${app.ai-reply.cache-ttl:10m}")
    private Duration cacheTtl;

    @Value("${app.ai-reply.prompt-version:v1}")
    private String promptVersion;

    @Value("${app.rate-limit.ai-reply.limit:20}")
    private int rateLimit;

    @Value("${app.rate-limit.ai-reply.window-seconds:3600}")
    private long rateWindowSeconds;

    @Override
    public ReplySuggestionsResponse suggestReplies(String conversationId, String requestedLastMessageId) {
        User requester = userService.getCurrentAuthenticatedUser();
        Conversation conversation = requireMember(conversationId, requester);
        Context context = loadContext(conversation, requester);
        String cacheKey = cacheKey("reply", requester.getId(), conversationId, context.lastMessageId());

        List<String> cached = readCache(cacheKey);
        if (!cached.isEmpty()) {
            return response(cached, context.lastMessageId(), true);
        }

        checkRateLimit(requester);
        String prompt = """
                Bạn đang hỗ trợ người dùng soạn câu trả lời trong ứng dụng nhắn tin.
                Dựa trên đoạn hội thoại bên dưới, tạo đúng 3 câu trả lời ngắn bằng ngôn ngữ đang được dùng.
                Mỗi câu tự nhiên, phù hợp ngữ cảnh, khác sắc thái nhẹ, tối đa 150 ký tự.
                Không tự nhận là AI, không Markdown, không thêm giải thích.
                Trả về JSON hợp lệ duy nhất theo dạng {"suggestions":["...","...","..."]}.

                Hội thoại:
                """ + context.transcript();
        List<String> suggestions = requestSuggestions(prompt);
        writeCache(cacheKey, suggestions);
        return response(suggestions, context.lastMessageId(), false);
    }

    @Override
    public BirthdayContextResponse getBirthdayContext(String conversationId) {
        User requester = userService.getCurrentAuthenticatedUser();
        Conversation conversation = requireMember(conversationId, requester);
        if (conversation.getType() != ConversationType.PRIVATE || !requester.isBirthdayReminderEnabled()) {
            return noBirthday();
        }

        User birthdayUser = conversation.getMembers().stream()
                .filter(member -> !Objects.equals(member.getId(), requester.getId()))
                .findFirst()
                .orElse(null);
        if (birthdayUser == null
                || birthdayUser.getBirthdayVisibility() != BirthdayVisibility.FRIENDS
                || !areAcceptedFriends(requester, birthdayUser)) {
            return noBirthday();
        }

        Integer daysUntil = daysUntilBirthday(birthdayUser.getBirthday());
        if (daysUntil == null || daysUntil > 7) {
            return noBirthday();
        }

        String message = daysUntil == 0
                ? "Hôm nay là sinh nhật của " + birthdayUser.getUsername() + "."
                : "Còn " + daysUntil + " ngày nữa là sinh nhật của " + birthdayUser.getUsername() + ".";
        return BirthdayContextResponse.builder()
                .hasBirthday(true)
                .userId(birthdayUser.getId())
                .displayName(birthdayUser.getUsername())
                .daysUntil(daysUntil)
                .message(message)
                .templates(DEFAULT_BIRTHDAY_WISHES)
                .build();
    }

    @Override
    public ReplySuggestionsResponse personalizeBirthdayWishes(String conversationId, String requestedLastMessageId) {
        User requester = userService.getCurrentAuthenticatedUser();
        Conversation conversation = requireMember(conversationId, requester);
        BirthdayContextResponse birthday = getBirthdayContext(conversationId);
        if (!birthday.isHasBirthday()) {
            throw new BadRequestException("Không có thông tin sinh nhật khả dụng cho cuộc trò chuyện này");
        }

        Context context = loadContext(conversation, requester);
        String cacheKey = cacheKey("birthday", requester.getId(), birthday.getUserId(), context.lastMessageId());
        List<String> cached = readCache(cacheKey);
        if (!cached.isEmpty()) {
            return response(cached, context.lastMessageId(), true);
        }

        checkRateLimit(requester);
        String prompt = """
                Viết đúng 3 lời chúc sinh nhật ngắn, ấm áp và tự nhiên bằng tiếng Việt cho %s.
                Cá nhân hóa nhẹ dựa trên hội thoại, nhưng không bịa sự kiện, sở thích hay mối quan hệ.
                Mỗi lời chúc tối đa 180 ký tự, không Markdown, không giải thích.
                Trả về JSON hợp lệ duy nhất theo dạng {"suggestions":["...","...","..."]}.

                Hội thoại gần đây:
                %s
                """.formatted(birthday.getDisplayName(), context.transcript());
        List<String> suggestions = requestSuggestions(prompt);
        writeCache(cacheKey, suggestions);
        return response(suggestions, context.lastMessageId(), false);
    }

    private Conversation requireMember(String conversationId, User requester) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        boolean member = conversation.getMembers() != null && conversation.getMembers().stream()
                .anyMatch(user -> Objects.equals(user.getId(), requester.getId()));
        if (!member) {
            throw new ResourceNotFoundException("Conversation not found");
        }
        return conversation;
    }

    private Context loadContext(Conversation conversation, User requester) {
        int safeLimit = Math.max(5, Math.min(messageLimit, 10));
        List<Message> recent = messageRepository.findVisibleConversationMessages(
                conversation.getId(),
                requester.getId(),
                PageRequest.of(0, safeLimit)
        ).getContent();
        if (recent.isEmpty()) {
            throw new BadRequestException("Cuộc trò chuyện chưa có tin nhắn để tạo gợi ý");
        }

        String lastMessageId = recent.get(0).getId();
        List<String> lines = new ArrayList<>();
        for (int index = recent.size() - 1; index >= 0; index--) {
            Message message = recent.get(index);
            if (message.isRecalled() || message.getMessageType() == MessageType.SYSTEM) {
                continue;
            }
            String content = Jsoup.parse(message.getContent() == null ? "" : message.getContent()).text().trim();
            if (content.isBlank()) {
                continue;
            }
            String sender = Objects.equals(message.getSenderId(), requester.getId())
                    || (message.getSender() != null && Objects.equals(message.getSender().getId(), requester.getId()))
                    ? "Tôi"
                    : firstNonBlank(message.getSenderUsername(),
                    message.getSender() == null ? null : message.getSender().getUsername(), "Người khác");
            lines.add(sender + ": " + content);
        }
        if (lines.isEmpty()) {
            throw new BadRequestException("Không có nội dung văn bản phù hợp để tạo gợi ý");
        }
        return new Context(lastMessageId, String.join("\n", lines));
    }

    private List<String> requestSuggestions(String prompt) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new BadRequestException("Tính năng gợi ý AI chưa được cấu hình");
        }

        List<String> modelsToTry = new ArrayList<>();
        if (geminiModel != null && !geminiModel.isBlank()) {
            modelsToTry.add(geminiModel.trim());
        }
        for (String fallbackModel : List.of("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")) {
            if (!modelsToTry.contains(fallbackModel)) {
                modelsToTry.add(fallbackModel);
            }
        }

        Map<String, Object> payload = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "maxOutputTokens", 2048,
                        "responseMimeType", "application/json"
                )
        );

        Exception lastException = null;
        for (String modelName : modelsToTry) {
            try {
                ResponseEntity<Object> response = restTemplate.postForEntity(
                        geminiUrl, payload, Object.class, modelName, geminiApiKey);
                String json = extractGeminiText(response.getBody());
                List<String> suggestions = parseSuggestions(json);
                if (!suggestions.isEmpty()) {
                    if (!Objects.equals(modelName, geminiModel)) {
                        log.info("Successfully generated reply suggestions using fallback Gemini model {}", modelName);
                    }
                    return suggestions;
                }
            } catch (Exception exception) {
                lastException = exception;
                log.warn("Gemini model {} failed for reply suggestions: {}. Trying fallback model...",
                        modelName, exception.getMessage());
            }
        }

        log.error("All Gemini fallback models failed for reply suggestions", lastException);
        throw new BadRequestException("Dịch vụ AI đang bận hoặc bị giới hạn tần suất. Vui lòng thử lại sau giây lát.");
    }

    @SuppressWarnings("unchecked")
    private String extractGeminiText(Object body) {
        if (!(body instanceof Map<?, ?> root)) return null;
        Object candidatesObject = root.get("candidates");
        if (!(candidatesObject instanceof List<?> candidates) || candidates.isEmpty()) return null;
        Object first = candidates.get(0);
        if (!(first instanceof Map<?, ?> candidate)) return null;
        Object contentObject = candidate.get("content");
        if (!(contentObject instanceof Map<?, ?> content)) return null;
        Object partsObject = content.get("parts");
        if (!(partsObject instanceof List<?> parts) || parts.isEmpty()) return null;

        StringBuilder textBuilder = new StringBuilder();
        for (Object partObject : parts) {
            if (partObject instanceof Map<?, ?> part) {
                Object text = part.get("text");
                if (text != null) {
                    textBuilder.append(text);
                }
            }
        }
        String result = textBuilder.toString().trim();
        return result.isBlank() ? null : result;
    }

    private List<String> parseSuggestions(String rawJson) throws Exception {
        if (rawJson == null || rawJson.isBlank()) return List.of();
        String json = rawJson.trim()
                .replaceFirst("(?s)^```(?:json)?\\s*", "")
                .replaceFirst("(?s)\\s*```$", "");
        Object parsed = objectMapper.readValue(json, Object.class);
        Object values = parsed;
        if (parsed instanceof Map<?, ?> map) {
            values = map.get("suggestions");
        }
        if (!(values instanceof List<?> list)) return List.of();
        return list.stream()
                .map(Object::toString)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.length() > 180 ? value.substring(0, 180) : value)
                .distinct()
                .limit(3)
                .toList();
    }

    private List<String> readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) return List.of();
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void writeCache(String key, List<String> suggestions) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(suggestions), cacheTtl);
        } catch (Exception ignored) {
            // Suggestions remain usable when Redis is unavailable.
        }
    }

    private void checkRateLimit(User requester) {
        rateLimitService.check(
                "ai:reply",
                requester.getId(),
                rateLimit,
                Duration.ofSeconds(Math.max(1, rateWindowSeconds)),
                "Bạn đã dùng quá nhiều lượt gợi ý AI. Vui lòng thử lại sau."
        );
    }

    private boolean areAcceptedFriends(User requester, User other) {
        return friendshipRepository.findFriendshipBetweenUsers(requester.getId(), other.getId())
                .map(friendship -> friendship.getStatus() == FriendshipStatus.ACCEPTED)
                .orElse(false);
    }

    private Integer daysUntilBirthday(String birthday) {
        if (birthday == null || birthday.isBlank()) return null;
        try {
            MonthDay monthDay = birthday.length() > 5
                    ? MonthDay.from(LocalDate.parse(birthday))
                    : MonthDay.parse("--" + birthday);
            LocalDate today = LocalDate.now();
            LocalDate next = monthDay.atYear(today.getYear());
            if (next.isBefore(today)) next = monthDay.atYear(today.getYear() + 1);
            return Math.toIntExact(ChronoUnit.DAYS.between(today, next));
        } catch (Exception ignored) {
            return null;
        }
    }

    private ReplySuggestionsResponse response(List<String> suggestions, String messageId, boolean cached) {
        return ReplySuggestionsResponse.builder()
                .suggestions(suggestions)
                .basedOnMessageId(messageId)
                .cached(cached)
                .build();
    }

    private BirthdayContextResponse noBirthday() {
        return BirthdayContextResponse.builder()
                .hasBirthday(false)
                .templates(List.of())
                .build();
    }

    private String cacheKey(String type, String userId, String contextId, String lastMessageId) {
        return "nextalk:ai:" + type + ":" + promptVersion + ":" + userId + ":" + contextId + ":"
                + (lastMessageId == null ? "none" : lastMessageId);
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return fallback;
    }

    private record Context(String lastMessageId, String transcript) {
    }
}
