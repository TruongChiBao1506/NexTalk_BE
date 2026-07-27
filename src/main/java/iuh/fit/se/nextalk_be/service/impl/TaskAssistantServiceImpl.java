package iuh.fit.se.nextalk_be.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.nextalk_be.dto.request.CreateChannelTaskRequest;
import iuh.fit.se.nextalk_be.dto.request.CreateMessageReminderRequest;
import iuh.fit.se.nextalk_be.dto.request.TaskAssistantRequest;
import iuh.fit.se.nextalk_be.dto.response.ChannelTaskResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageReminderResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageResponse;
import iuh.fit.se.nextalk_be.dto.response.TaskAssistantActionResponse;
import iuh.fit.se.nextalk_be.dto.response.TaskAssistantResponse;
import iuh.fit.se.nextalk_be.entity.ChannelTaskPriority;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.entity.TaskAssistantPendingAction;
import iuh.fit.se.nextalk_be.exception.AppException;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.TaskAssistantPendingActionRepository;
import iuh.fit.se.nextalk_be.service.ChannelTaskService;
import iuh.fit.se.nextalk_be.service.MessageReminderService;
import iuh.fit.se.nextalk_be.service.MessageService;
import iuh.fit.se.nextalk_be.service.TaskAssistantService;
import iuh.fit.se.nextalk_be.service.UserService;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TaskAssistantServiceImpl implements TaskAssistantService {
    private static final Set<String> READ_TOOLS = Set.of("search_messages", "get_conversation_context");
    private static final Set<String> MUTATION_TOOLS = Set.of("create_channel_task", "schedule_reminder");
    private static final int MAX_TOOL_TURNS = 6;
    private static final int INITIAL_CONTEXT_MESSAGE_LIMIT = 16;
    private static final DateTimeFormatter VIETNAMESE_DATE_TIME =
            DateTimeFormatter.ofPattern("HH:mm, EEEE 'ngày' dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));
    private static final Pattern FAST_REMINDER_INTENT = Pattern.compile(
            "(?iu)\\bnhắc\\s+(?:tôi|mình)\\b.*\\b(?:tin\\s*nhắn|message)\\b"
    );
    private static final Pattern LATEST_MESSAGE_REFERENCE = Pattern.compile(
            "(?iu)\\b(?:tin\\s*nhắn\\s*(?:mới\\s*nhất|gần\\s*nhất|này)|message\\s*(?:mới\\s*nhất|gần\\s*nhất|này))\\b"
    );
    private static final Pattern RELATIVE_REMINDER_TIME = Pattern.compile(
            "(?iu)(?:sau\\s*)?(\\d{1,4})\\s*(phút|phut|giờ|gio|tiếng|tieng|ngày|ngay)\\s*(?:nữa|nua|sau)?"
    );

    private final ConversationRepository conversationRepository;
    private final ChannelRepository channelRepository;
    private final MessageRepository messageRepository;
    private final TaskAssistantPendingActionRepository pendingActionRepository;
    private final MessageService messageService;
    private final MessageReminderService messageReminderService;
    private final ChannelTaskService channelTaskService;
    private final UserService userService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.task-assistant.enabled:false}")
    private boolean enabled;

    @Value("${app.task-assistant.api-key:${GEMINI_API_KEY:}}")
    private String apiKey;

    @Value("${app.task-assistant.agent:antigravity-preview-05-2026}")
    private String agent;

    @Value("${app.task-assistant.model:gemini-3.6-flash}")
    private String model;

    @Value("${app.task-assistant.url:https://generativelanguage.googleapis.com/v1beta/interactions}")
    private String interactionsUrl;

    @Value("${app.task-assistant.max-total-tokens:20000}")
    private int maxTotalTokens;

    @Value("${app.task-assistant.confirmation-minutes:15}")
    private int confirmationMinutes;

    @Override
    public TaskAssistantResponse ask(TaskAssistantRequest request) {
        assertConfigured();
        User user = userService.getCurrentAuthenticatedUser();
        Conversation conversation = accessibleConversation(request.getConversationId(), user);

        InteractionContext context = new InteractionContext(
                user,
                conversation,
                blankToNull(request.getGroupId()),
                blankToNull(request.getChannelId())
        );
        TaskAssistantResponse fastResponse = tryFastReminder(request.getPrompt(), context);
        if (fastResponse != null) {
            return fastResponse;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent", agent);
        payload.put("input", List.of(Map.of("type", "text", "text", request.getPrompt().trim())));
        payload.put("environment", Map.of("type", "remote"));
        payload.put("store", true);
        payload.put("system_instruction", buildSystemInstruction(context));
        payload.put("agent_config", Map.of(
                "type", "antigravity",
                "model", model,
                "max_total_tokens", Math.max(5_000, Math.min(maxTotalTokens, 100_000))
        ));
        payload.put("tools", toolDefinitions());

        return resolveInteraction(postInteraction(payload), context);
    }

    private TaskAssistantResponse tryFastReminder(String rawPrompt, InteractionContext context) {
        String prompt = rawPrompt == null ? "" : rawPrompt.trim();
        if (!FAST_REMINDER_INTENT.matcher(prompt).find()
                || !LATEST_MESSAGE_REFERENCE.matcher(prompt).find()) {
            return null;
        }

        Matcher timeMatcher = RELATIVE_REMINDER_TIME.matcher(prompt);
        if (!timeMatcher.find()) return null;

        long amount;
        try {
            amount = Long.parseLong(timeMatcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
        if (amount <= 0) return null;

        String unit = timeMatcher.group(2).toLowerCase(Locale.ROOT);
        long minutes;
        if (unit.startsWith("ph")) {
            minutes = amount;
        } else if (unit.startsWith("gi") || unit.startsWith("ti")) {
            minutes = amount * 60;
        } else {
            minutes = amount * 24 * 60;
        }
        if (minutes > 525_600) return null;

        Message latestMessage = latestVisibleMessage(context);
        if (latestMessage == null) return null;

        LocalDateTime remindAt = LocalDateTime.now().plusMinutes(minutes);
        String note = prompt.substring(0, timeMatcher.start())
                .replaceFirst("(?iu)^\\s*nhắc\\s+(?:tôi|mình)\\s*", "")
                .replaceAll("[,;:\\-\\s]+$", "")
                .trim();
        if (note.isBlank()) note = "Kiểm tra lại tin nhắn";
        note = Character.toUpperCase(note.charAt(0)) + note.substring(1);

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("messageId", latestMessage.getId());
        arguments.put("remindAt", remindAt.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString());
        arguments.put("note", shorten(note, 180));

        TaskAssistantPendingAction pending = TaskAssistantPendingAction.builder()
                .userId(context.user().getId())
                .conversationId(context.conversation().getId())
                .groupId(context.groupId())
                .channelId(context.channelId())
                .toolName("schedule_reminder")
                .arguments(arguments)
                .status("PENDING")
                .expiresAt(LocalDateTime.now().plusMinutes(Math.max(1, confirmationMinutes)))
                .build();
        pending = pendingActionRepository.save(pending);

        return TaskAssistantResponse.builder()
                .status("CONFIRMATION_REQUIRED")
                .reply("Mình đã hiểu yêu cầu. Hãy kiểm tra thời gian trước khi xác nhận.")
                .confirmationId(pending.getId())
                .action(toActionResponse(pending))
                .build();
    }

    private Message latestVisibleMessage(InteractionContext context) {
        var page = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                context.conversation().getId(),
                PageRequest.of(0, 10)
        );
        if (page == null) return null;
        return page.getContent().stream()
                .filter(message -> !message.isRecalled())
                .filter(message -> message.getDeletedByUsers() == null
                        || !message.getDeletedByUsers().contains(context.user().getId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public TaskAssistantResponse confirm(String confirmationId) {
        User user = userService.getCurrentAuthenticatedUser();
        TaskAssistantPendingAction pending = pendingActionRepository
                .findByIdAndUserId(confirmationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Assistant confirmation not found"));
        if (!"PENDING".equals(pending.getStatus())) {
            throw new BadRequestException("Yêu cầu này đã được xử lý.");
        }
        if (pending.getExpiresAt() == null || pending.getExpiresAt().isBefore(LocalDateTime.now())) {
            pending.setStatus("EXPIRED");
            pendingActionRepository.save(pending);
            throw new BadRequestException("Yêu cầu xác nhận đã hết hạn.");
        }

        Conversation conversation = accessibleConversation(pending.getConversationId(), user);
        InteractionContext context = new InteractionContext(
                user,
                conversation,
                pending.getGroupId(),
                pending.getChannelId()
        );

        Map<String, Object> localResult;
        pending.setStatus("EXECUTING");
        pendingActionRepository.save(pending);
        try {
            localResult = executeMutation(pending, context);
            pending.setStatus("COMPLETED");
            pendingActionRepository.save(pending);
        } catch (RuntimeException exception) {
            pending.setStatus("PENDING");
            pendingActionRepository.save(pending);
            throw exception;
        }

        // The NexTalk mutation has already succeeded. Returning the deterministic
        // local result avoids an unnecessary second Antigravity request and makes
        // confirmation feel as fast as the manual reminder/task flow.
        return TaskAssistantResponse.builder()
                .status("COMPLETED")
                .reply(localSuccessMessage(pending.getToolName()))
                .result(localResult)
                .build();
    }

    @Override
    public TaskAssistantResponse reject(String confirmationId) {
        User user = userService.getCurrentAuthenticatedUser();
        TaskAssistantPendingAction pending = pendingActionRepository
                .findByIdAndUserId(confirmationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Assistant confirmation not found"));
        if (!"PENDING".equals(pending.getStatus())) {
            throw new BadRequestException("Yêu cầu này đã được xử lý.");
        }
        pending.setStatus("REJECTED");
        pendingActionRepository.save(pending);
        return TaskAssistantResponse.builder()
                .status("REJECTED")
                .reply("Đã hủy thao tác. NexTalk chưa thay đổi dữ liệu nào.")
                .build();
    }

    private TaskAssistantResponse resolveInteraction(
            Map<String, Object> initialInteraction,
            InteractionContext context
    ) {
        Map<String, Object> interaction = initialInteraction;
        for (int turn = 0; turn < MAX_TOOL_TURNS; turn++) {
            String status = stringValue(interaction.get("status")).toLowerCase(Locale.ROOT);
            if ("completed".equals(status) || "incomplete".equals(status)) {
                String reply = stringValue(interaction.get("output_text"));
                if (reply.isBlank()) {
                    reply = "Trợ lý đã hoàn tất nhưng không trả về nội dung.";
                }
                return TaskAssistantResponse.builder()
                        .status("COMPLETED")
                        .reply(reply)
                        .build();
            }
            if ("failed".equals(status) || "cancelled".equals(status)) {
                throw new AppException(HttpStatus.BAD_GATEWAY, "Antigravity không thể hoàn thành tác vụ này.");
            }
            if (!"requires_action".equals(status)) {
                throw new AppException(HttpStatus.BAD_GATEWAY, "Antigravity trả về trạng thái không hợp lệ.");
            }

            PendingFunctionCall call = findPendingFunctionCall(interaction);
            if (call == null) {
                throw new AppException(HttpStatus.BAD_GATEWAY, "Antigravity không cung cấp function call hợp lệ.");
            }
            if (MUTATION_TOOLS.contains(call.name())) {
                String contextError = validateMutationContext(call, context);
                if (contextError == null) {
                    TaskAssistantPendingAction pending = savePendingAction(interaction, call, context);
                    return TaskAssistantResponse.builder()
                            .status("CONFIRMATION_REQUIRED")
                            .reply("Trợ lý cần bạn xác nhận trước khi thay đổi dữ liệu NexTalk.")
                            .confirmationId(pending.getId())
                            .action(toActionResponse(pending))
                            .build();
                }
                interaction = postInteraction(continuationPayload(
                        stringValue(interaction.get("id")),
                        stringValue(interaction.get("environment_id")),
                        call.id(),
                        call.name(),
                        Map.of("success", false, "error", contextError)
                ));
                continue;
            }

            Map<String, Object> result = READ_TOOLS.contains(call.name())
                    ? executeReadTool(call, context)
                    : Map.of("success", false, "error", "Tool không được NexTalk cho phép");
            interaction = postInteraction(continuationPayload(
                    stringValue(interaction.get("id")),
                    stringValue(interaction.get("environment_id")),
                    call.id(),
                    call.name(),
                    result
            ));
        }
        throw new AppException(HttpStatus.BAD_GATEWAY, "Trợ lý đã vượt quá số bước xử lý cho phép.");
    }

    private Map<String, Object> executeReadTool(PendingFunctionCall call, InteractionContext context) {
        return switch (call.name()) {
            case "search_messages" -> {
                String query = requiredString(call.arguments(), "query", 200);
                List<Map<String, Object>> messages = messageService
                        .searchMessages(query, context.conversation().getId())
                        .stream()
                        .limit(20)
                        .map(this::messageResult)
                        .toList();
                yield Map.of("success", true, "count", messages.size(), "messages", messages);
            }
            case "get_conversation_context" -> {
                int requestedLimit = integerValue(call.arguments().get("limit"), 20);
                int limit = Math.max(1, Math.min(requestedLimit, 40));
                List<Map<String, Object>> messages = messageRepository
                        .findByConversationIdOrderByCreatedAtDesc(
                                context.conversation().getId(),
                                PageRequest.of(0, limit)
                        )
                        .getContent()
                        .stream()
                        .filter(message -> !message.isRecalled())
                        .filter(message -> message.getDeletedByUsers() == null
                                || !message.getDeletedByUsers().contains(context.user().getId()))
                        .map(this::messageResult)
                        .toList();
                yield Map.of("success", true, "count", messages.size(), "messages", messages);
            }
            default -> Map.of("success", false, "error", "Tool không được hỗ trợ");
        };
    }

    private Map<String, Object> executeMutation(
            TaskAssistantPendingAction pending,
            InteractionContext context
    ) {
        Map<String, Object> arguments = pending.getArguments();
        return switch (pending.getToolName()) {
            case "schedule_reminder" -> {
                String messageId = requiredString(arguments, "messageId", 100);
                Message sourceMessage = messageRepository.findById(messageId)
                        .filter(message -> context.conversation().getId().equals(message.getConversationId()))
                        .orElseThrow(() -> new BadRequestException(
                                "Tin nhắn dùng để tạo lời nhắc không thuộc cuộc trò chuyện hiện tại."
                        ));
                MessageReminderResponse reminder = messageReminderService.createReminder(
                        CreateMessageReminderRequest.builder()
                                .messageId(sourceMessage.getId())
                                .remindAt(requiredString(arguments, "remindAt", 80))
                                .note(optionalString(arguments, "note", 500))
                                .build()
                );
                yield Map.of(
                        "success", true,
                        "reminderId", reminder.getId(),
                        "messageId", reminder.getMessageId(),
                        "remindAt", reminder.getRemindAt(),
                        "note", reminder.getNote() == null ? "" : reminder.getNote()
                );
            }
            case "create_channel_task" -> {
                Set<String> assigneeIds = stringSet(arguments.get("assigneeIds"), 20);
                ChannelTaskResponse task = channelTaskService.createTask(
                        context.groupId(),
                        context.channelId(),
                        CreateChannelTaskRequest.builder()
                                .title(requiredString(arguments, "title", 200))
                                .description(optionalString(arguments, "description", 1000))
                                .priority(priorityValue(arguments.get("priority")))
                                .dueAt(optionalString(arguments, "dueAt", 80))
                                .assigneeIds(assigneeIds)
                                .sourceMessageId(optionalString(arguments, "sourceMessageId", 100))
                                .build()
                );
                yield Map.of(
                        "success", true,
                        "taskId", task.getId(),
                        "title", task.getTitle(),
                        "status", task.getStatus(),
                        "priority", task.getPriority()
                );
            }
            default -> throw new BadRequestException("Tool không được phép thực thi.");
        };
    }

    private TaskAssistantPendingAction savePendingAction(
            Map<String, Object> interaction,
            PendingFunctionCall call,
            InteractionContext context
    ) {
        Map<String, Object> safeArguments = normalizeMutationArguments(call.name(), call.arguments());
        TaskAssistantPendingAction pending = TaskAssistantPendingAction.builder()
                .userId(context.user().getId())
                .conversationId(context.conversation().getId())
                .groupId(context.groupId())
                .channelId(context.channelId())
                .interactionId(requiredInteractionField(interaction, "id"))
                .environmentId(requiredInteractionField(interaction, "environment_id"))
                .callId(call.id())
                .toolName(call.name())
                .arguments(safeArguments)
                .status("PENDING")
                .expiresAt(LocalDateTime.now().plusMinutes(Math.max(1, confirmationMinutes)))
                .build();
        return pendingActionRepository.save(pending);
    }

    private Map<String, Object> normalizeMutationArguments(String toolName, Map<String, Object> arguments) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if ("schedule_reminder".equals(toolName)) {
            safe.put("messageId", requiredString(arguments, "messageId", 100));
            safe.put("remindAt", requiredString(arguments, "remindAt", 80));
            safe.put("note", optionalString(arguments, "note", 500));
            return safe;
        }
        safe.put("title", requiredString(arguments, "title", 200));
        safe.put("description", optionalString(arguments, "description", 1000));
        safe.put("priority", priorityValue(arguments.get("priority")).name());
        safe.put("dueAt", optionalString(arguments, "dueAt", 80));
        safe.put("assigneeIds", new ArrayList<>(stringSet(arguments.get("assigneeIds"), 20)));
        safe.put("sourceMessageId", optionalString(arguments, "sourceMessageId", 100));
        return safe;
    }

    private TaskAssistantActionResponse toActionResponse(TaskAssistantPendingAction pending) {
        Map<String, Object> arguments = pending.getArguments();
        if ("schedule_reminder".equals(pending.getToolName())) {
            return TaskAssistantActionResponse.builder()
                    .toolName(pending.getToolName())
                    .label("Tạo lời nhắc")
                    .summary("Nhắc lúc " + friendlyDateTime(arguments.get("remindAt"))
                            + (stringValue(arguments.get("note")).isBlank() ? "" : ": " + arguments.get("note")))
                    .arguments(arguments)
                    .build();
        }
        return TaskAssistantActionResponse.builder()
                .toolName(pending.getToolName())
                .label("Tạo công việc")
                .summary(stringValue(arguments.get("title"))
                        + (stringValue(arguments.get("dueAt")).isBlank()
                            ? ""
                            : " · Hạn " + friendlyDateTime(arguments.get("dueAt"))))
                .arguments(arguments)
                .build();
    }

    private Map<String, Object> postInteraction(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey.trim());
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    interactionsUrl,
                    new HttpEntity<>(payload, headers),
                    Map.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AppException(HttpStatus.BAD_GATEWAY, "Antigravity không trả về phản hồi hợp lệ.");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = response.getBody();
            return body;
        } catch (HttpStatusCodeException exception) {
            int status = exception.getStatusCode().value();
            if (status == 429) {
                throw new AppException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Antigravity đã đạt giới hạn sử dụng của Gemini API. Vui lòng thử lại sau."
                );
            }
            if (status == 400) {
                throw new AppException(
                        HttpStatus.BAD_REQUEST,
                        "Antigravity không thể xử lý yêu cầu này hoặc cấu hình agent chưa được hỗ trợ."
                );
            }
            if (status == 401 || status == 403) {
                throw new AppException(
                        HttpStatus.BAD_GATEWAY,
                        "API key chưa được cấp quyền sử dụng Antigravity."
                );
            }
            throw new AppException(HttpStatus.BAD_GATEWAY, "Không thể gọi Antigravity lúc này.");
        } catch (RestClientException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "Không thể kết nối đến Antigravity.");
        }
    }

    private Map<String, Object> continuationPayload(
            String interactionId,
            String environmentId,
            String callId,
            String toolName,
            Map<String, Object> result
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agent", agent);
        payload.put("previous_interaction_id", interactionId);
        payload.put("environment", environmentId);
        payload.put("input", List.of(Map.of(
                "type", "function_result",
                "name", toolName,
                "call_id", callId,
                "result", result
        )));
        return payload;
    }

    private PendingFunctionCall findPendingFunctionCall(Map<String, Object> interaction) {
        Object rawSteps = interaction.get("steps");
        if (!(rawSteps instanceof List<?> steps)) return null;

        Set<String> completedCallIds = new HashSet<>();
        for (Object rawStep : steps) {
            if (rawStep instanceof Map<?, ?> step && "function_result".equals(stringValue(step.get("type")))) {
                completedCallIds.add(stringValue(step.get("call_id")));
            }
        }
        for (int index = steps.size() - 1; index >= 0; index--) {
            Object rawStep = steps.get(index);
            if (!(rawStep instanceof Map<?, ?> step)
                    || !"function_call".equals(stringValue(step.get("type")))) {
                continue;
            }
            String id = stringValue(step.get("id"));
            if (id.isBlank() || completedCallIds.contains(id)) continue;
            String name = stringValue(step.get("name"));
            Map<String, Object> arguments = parseArguments(step.get("arguments"));
            return new PendingFunctionCall(id, name, arguments);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(Object rawArguments) {
        if (rawArguments instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        if (rawArguments instanceof String json && !json.isBlank()) {
            try {
                return objectMapper.readValue(json, new TypeReference<>() {});
            } catch (Exception exception) {
                throw new BadRequestException("Function arguments từ Antigravity không hợp lệ.");
            }
        }
        return new LinkedHashMap<>();
    }

    private List<Map<String, Object>> toolDefinitions() {
        return List.of(
                functionTool(
                        "search_messages",
                        "Tìm các tin nhắn văn bản mà người dùng có quyền xem trong cuộc trò chuyện hiện tại.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of("type", "string", "description", "Từ khóa cần tìm")
                                ),
                                "required", List.of("query")
                        )
                ),
                functionTool(
                        "get_conversation_context",
                        "Đọc tối đa 40 tin nhắn gần nhất trong cuộc trò chuyện hiện tại.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "limit", Map.of("type", "integer", "description", "Số tin nhắn, từ 1 đến 40")
                                )
                        )
                ),
                functionTool(
                        "schedule_reminder",
                        "Đề xuất tạo lời nhắc cho một messageId đã tìm thấy. Luôn cần người dùng xác nhận.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "messageId", Map.of("type", "string"),
                                        "remindAt", Map.of("type", "string", "description", "Thời gian ISO 8601 có múi giờ"),
                                        "note", Map.of("type", "string")
                                ),
                                "required", List.of("messageId", "remindAt")
                        )
                ),
                functionTool(
                        "create_channel_task",
                        "Đề xuất tạo công việc trong channel hiện tại. Luôn cần người dùng xác nhận.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "title", Map.of("type", "string"),
                                        "description", Map.of("type", "string"),
                                        "priority", Map.of(
                                                "type", "string",
                                                "enum", List.of("LOW", "MEDIUM", "HIGH", "URGENT")
                                        ),
                                        "dueAt", Map.of("type", "string", "description", "Thời hạn ISO 8601 có múi giờ"),
                                        "assigneeIds", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "string")
                                        ),
                                        "sourceMessageId", Map.of("type", "string")
                                ),
                                "required", List.of("title")
                        )
                )
        );
    }

    private Map<String, Object> functionTool(
            String name,
            String description,
            Map<String, Object> parameters
    ) {
        return Map.of(
                "type", "function",
                "name", name,
                "description", description,
                "parameters", parameters
        );
    }

    private String buildSystemInstruction(InteractionContext context) {
        StringBuilder members = new StringBuilder();
        context.conversation().getMembers().stream().limit(100).forEach(member -> members
                .append("- ")
                .append(member.getUsername())
                .append(" (userId: ")
                .append(member.getId())
                .append(")\n"));

        String channelContext = context.groupId() != null && context.channelId() != null
                ? "Channel task context is available. groupId=" + context.groupId()
                    + ", channelId=" + context.channelId() + "."
                : "No channel task context is available. Do not call create_channel_task.";
        return """
                Bạn là Trợ lý tác vụ của NexTalk. Trả lời bằng tiếng Việt, ngắn gọn và chính xác.
                Chỉ sử dụng các function được cung cấp; không được giả định rằng dữ liệu đã thay đổi.
                Hãy gọi search_messages hoặc get_conversation_context để lấy messageId thật trước khi tạo lời nhắc.
                Khi cần thay đổi dữ liệu, hãy gọi function tương ứng; NexTalk sẽ yêu cầu người dùng xác nhận.
                Không tự tạo userId, messageId, ngày giờ hoặc thông tin không có trong yêu cầu/ngữ cảnh.
                Ngày giờ hiện tại của máy chủ: %s.
                Người yêu cầu: %s (userId: %s).
                Conversation ID: %s.
                %s
                Các tin nhắn gần nhất đã được NexTalk cung cấp bên dưới. Ưu tiên dùng trực tiếp messageId
                trong danh sách này, chỉ gọi tool đọc khi chưa đủ thông tin:
                %s
                Thành viên:
                %s
                """.formatted(
                LocalDateTime.now(),
                context.user().getUsername(),
                context.user().getId(),
                context.conversation().getId(),
                channelContext,
                recentConversationSnapshot(context),
                members.toString().trim()
        );
    }

    private String recentConversationSnapshot(InteractionContext context) {
        try {
            var page = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                    context.conversation().getId(),
                    PageRequest.of(0, INITIAL_CONTEXT_MESSAGE_LIMIT)
            );
            if (page == null || page.isEmpty()) return "(Chưa có tin nhắn phù hợp)";
            return page.getContent().stream()
                    .filter(message -> !message.isRecalled())
                    .filter(message -> message.getDeletedByUsers() == null
                            || !message.getDeletedByUsers().contains(context.user().getId()))
                    .map(message -> {
                        Map<String, Object> item = messageResult(message);
                        return "- [" + item.get("createdAt") + "] "
                                + item.get("senderUsername") + " — messageId=" + item.get("messageId")
                                + ": " + item.get("content");
                    })
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("(Chưa có tin nhắn phù hợp)");
        } catch (RuntimeException exception) {
            return "(Không thể tải trước ngữ cảnh; hãy dùng tool đọc khi cần)";
        }
    }

    private String friendlyDateTime(Object raw) {
        String value = stringValue(raw).trim();
        if (value.isBlank()) return "";
        try {
            return OffsetDateTime.parse(value).format(VIETNAMESE_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value).format(VIETNAMESE_DATE_TIME);
            } catch (DateTimeParseException ignoredAgain) {
                return value;
            }
        }
    }

    private Conversation accessibleConversation(String conversationId, User user) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        boolean member = conversation.getMembers() != null && conversation.getMembers().stream()
                .anyMatch(item -> item.getId().equals(user.getId()));
        if (!member) {
            throw new ResourceNotFoundException("Conversation not found");
        }
        return conversation;
    }

    private String validateMutationContext(PendingFunctionCall call, InteractionContext context) {
        if ("schedule_reminder".equals(call.name())) {
            String messageId = optionalString(call.arguments(), "messageId", 100);
            if (messageId == null || messageRepository.findById(messageId)
                    .filter(message -> context.conversation().getId().equals(message.getConversationId()))
                    .isEmpty()) {
                return "Không thể tạo lời nhắc vì messageId không thuộc cuộc trò chuyện hiện tại.";
            }
        }
        if ("create_channel_task".equals(call.name())) {
            if (context.groupId() == null || context.channelId() == null) {
                return "Không thể tạo task vì cuộc trò chuyện hiện tại không phải channel có bật task.";
            }
            boolean validChannel = channelRepository.findById(context.channelId())
                    .filter(channel -> channel.isTaskEnabled())
                    .filter(channel -> channel.getGroup() != null
                            && context.groupId().equals(channel.getGroup().getId()))
                    .filter(channel -> channel.getConversation() != null
                            && context.conversation().getId().equals(channel.getConversation().getId()))
                    .isPresent();
            if (!validChannel) {
                return "Channel tạo task không khớp với cuộc trò chuyện hiện tại hoặc chưa bật task.";
            }
        }
        return null;
    }

    private Map<String, Object> messageResult(MessageResponse message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messageId", message.getId());
        result.put("senderUsername", message.getSenderUsername());
        result.put("content", shorten(Jsoup.parse(message.getContent() == null ? "" : message.getContent()).text(), 500));
        result.put("createdAt", message.getCreatedAt() == null ? "" : message.getCreatedAt().toString());
        return result;
    }

    private Map<String, Object> messageResult(Message message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messageId", message.getId());
        result.put("senderUsername", message.getSender() == null ? "" : message.getSender().getUsername());
        result.put("content", shorten(Jsoup.parse(message.getContent() == null ? "" : message.getContent()).text(), 500));
        result.put("createdAt", message.getCreatedAt() == null ? "" : message.getCreatedAt().toString());
        return result;
    }

    private String requiredInteractionField(Map<String, Object> interaction, String key) {
        String value = stringValue(interaction.get(key));
        if (value.isBlank()) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "Antigravity thiếu trạng thái phiên làm việc.");
        }
        return value;
    }

    private String requiredString(Map<String, Object> arguments, String key, int maxLength) {
        String value = optionalString(arguments, key, maxLength);
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Thiếu tham số " + key + ".");
        }
        return value;
    }

    private String optionalString(Map<String, Object> arguments, String key, int maxLength) {
        String value = stringValue(arguments.get(key)).trim();
        if (value.isBlank()) return null;
        if (value.length() > maxLength) {
            throw new BadRequestException("Tham số " + key + " vượt quá độ dài cho phép.");
        }
        return value;
    }

    private Set<String> stringSet(Object raw, int maxItems) {
        if (!(raw instanceof List<?> values)) return Set.of();
        Set<String> result = new HashSet<>();
        for (Object value : values) {
            String text = stringValue(value).trim();
            if (!text.isBlank()) result.add(text);
            if (result.size() >= maxItems) break;
        }
        return result;
    }

    private ChannelTaskPriority priorityValue(Object raw) {
        String value = stringValue(raw).trim().toUpperCase(Locale.ROOT);
        if (value.isBlank()) return ChannelTaskPriority.MEDIUM;
        try {
            return ChannelTaskPriority.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Mức ưu tiên task không hợp lệ.");
        }
    }

    private int integerValue(Object raw, int fallback) {
        if (raw instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(stringValue(raw));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String localSuccessMessage(String toolName) {
        return "schedule_reminder".equals(toolName)
                ? "Đã tạo lời nhắc thành công."
                : "Đã tạo công việc thành công.";
    }

    private String shorten(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value == null ? "" : value;
        return value.substring(0, maxLength - 1) + "…";
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void assertConfigured() {
        if (!enabled) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Trợ lý tác vụ đang tắt.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Trợ lý tác vụ chưa có GEMINI_API_KEY.");
        }
    }

    private record PendingFunctionCall(String id, String name, Map<String, Object> arguments) {}

    private record InteractionContext(
            User user,
            Conversation conversation,
            String groupId,
            String channelId
    ) {}
}
