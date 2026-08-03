package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.MessageService;

import iuh.fit.se.nextalk_be.dto.request.AddPollOptionRequest;
import iuh.fit.se.nextalk_be.dto.request.ChatRequestStatus;
import iuh.fit.se.nextalk_be.dto.request.CreatePollRequest;
import iuh.fit.se.nextalk_be.dto.request.EditMessageRequest;
import iuh.fit.se.nextalk_be.dto.request.MessageRequest;
import iuh.fit.se.nextalk_be.dto.request.PollVoteRequest;
import iuh.fit.se.nextalk_be.dto.request.ReactMessageRequest;
import iuh.fit.se.nextalk_be.dto.request.ShareMessageRequest;
import iuh.fit.se.nextalk_be.dto.request.TypingIndicatorRequest;
import iuh.fit.se.nextalk_be.dto.response.MessageResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageSearchResponse;
import iuh.fit.se.nextalk_be.dto.response.ConversationUnreadResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageDeliveryDetailsResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageDeliveryParticipantResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageSyncResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageStatusResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageStatusUpdateResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageAroundResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageCursorPageResponse;
import iuh.fit.se.nextalk_be.entity.Channel;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.ConversationUnreadMarker;
import iuh.fit.se.nextalk_be.entity.FriendshipStatus;
import iuh.fit.se.nextalk_be.entity.Group;
import iuh.fit.se.nextalk_be.entity.GroupRole;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageAttachment;
import iuh.fit.se.nextalk_be.entity.MessageReaction;
import iuh.fit.se.nextalk_be.entity.MessageNotificationDispatchStatus;
import iuh.fit.se.nextalk_be.entity.MessageStatus;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.event.TypingIndicatorEvent;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ConflictException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.ChatRequestRepository;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.ConversationUnreadMarkerRepository;
import iuh.fit.se.nextalk_be.repository.FriendshipRepository;
import iuh.fit.se.nextalk_be.repository.GroupMemberRepository;
import iuh.fit.se.nextalk_be.repository.GroupRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.MessageStatusRepository;
import iuh.fit.se.nextalk_be.repository.UserBlockRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.service.AiBotService;
import iuh.fit.se.nextalk_be.service.LinkPreviewService;
import iuh.fit.se.nextalk_be.service.MessageNotificationDispatcher;
import iuh.fit.se.nextalk_be.service.MessageCursorCodec;
import iuh.fit.se.nextalk_be.service.PresenceService;
import iuh.fit.se.nextalk_be.service.UserService;
import iuh.fit.se.nextalk_be.service.VoiceChannelService;
import iuh.fit.se.nextalk_be.service.MediaAuthorizationService;
import iuh.fit.se.nextalk_be.service.MessagePayloadValidator;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.TextCriteria;
import java.util.stream.Collectors;
import java.util.function.Function;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageServiceImpl implements MessageService {
    private static final int MAX_ATOMIC_MUTATION_ATTEMPTS = 5;
    private static final String LINK_PREVIEW_UPDATED_EVENT = "LINK_PREVIEW_UPDATED";
    private static final Pattern QUILL_MENTION_ID_PATTERN = Pattern.compile("data-id=[\"']([^\"']+)[\"']");
    private static final Pattern PLAIN_MENTION_PATTERN = Pattern.compile("(^|\\s)@([\\p{L}\\p{N}_\\.\\-]+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern BOT_MENTION_PATTERN = Pattern.compile("(^|\\s)@(bot|nextalk\\s+ai|meta\\s+ai)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern VOICE_INVITE_PATTERN = Pattern.compile("nextalk://voice/([^?\\s]+)\\?groupId=([^&\\s]+)");
    private static final Pattern WEB_LINK_PATTERN = Pattern.compile("(?i)https?://\\S+");
    private static final Pattern SUSPICIOUS_LINK_PATTERN = Pattern.compile(
            "(?i)(?:https?://)?(?:bit\\.ly|tinyurl\\.com|cutt\\.ly|\\d{1,3}(?:\\.\\d{1,3}){3})(?:/|\\b)|xn--"
    );

    private final MessageRepository messageRepository;
    private final GiphyMessageMetadataValidator giphyMessageMetadataValidator;
    private final MongoTemplate mongoTemplate;
    private final ConversationUnreadMarkerRepository conversationUnreadMarkerRepository;
    private final ConversationRepository conversationRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageStatusRepository messageStatusRepository;
    private final MessageNotificationDispatcher messageNotificationDispatcher;
    private final FriendshipRepository friendshipRepository;
    private final ChatRequestRepository chatRequestRepository;
    private final UserBlockRepository userBlockRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChannelRepository channelRepository;
    private final PresenceService presenceService;
    private final RateLimitService rateLimitService;
    private final LinkPreviewService linkPreviewService;
    private final MessageLinkPreviewEnricher messageLinkPreviewEnricher;
    private final LinkPreviewEnrichmentScheduler linkPreviewEnrichmentScheduler;
    private final AiBotService aiBotService;
    private final VoiceChannelService voiceChannelService;
    private final MediaAuthorizationService mediaAuthorizationService;
    private final MessagePayloadValidator messagePayloadValidator;

    @Value("${app.rate-limit.ai-bot.limit:10}")
    private int aiBotRateLimit;

    @Value("${app.rate-limit.ai-bot.window-seconds:60}")
    private long aiBotRateWindowSeconds;

    // @Transactional
    public MessageResponse sendMessage(MessageRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        return sendMessageWithUser(request, currentUser);
    }

    @Override
    public List<MessageResponse> getLatestMessages(List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) return List.of();
        User currentUser = userService.getCurrentAuthenticatedUser();
        Set<String> allowedIds = conversationRepository.findAllById(conversationIds).stream()
                .filter(c -> c.getMembers() != null && c.getMembers().stream().anyMatch(m -> m.getId().equals(currentUser.getId())))
                .map(Conversation::getId)
                .collect(Collectors.toSet());
        if (allowedIds.isEmpty()) return List.of();
        return mapMessagesToResponses(messageRepository.findLatestVisibleByConversationIds(new ArrayList<>(allowedIds), currentUser.getId()));
    }

    // @Transactional
    public MessageResponse sendMessage(MessageRequest request, String senderEmail) {
        User currentUser = userRepository.findByEmail(senderEmail)
                .or(() -> userRepository.findByUsername(senderEmail))
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
        return sendMessageWithUser(request, currentUser);
    }

    public void broadcastTypingIndicator(TypingIndicatorRequest request, String senderEmail) {
        User currentUser = userRepository.findByEmail(senderEmail)
                .or(() -> userRepository.findByUsername(senderEmail))
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
        rateLimitService.check("message:typing", currentUser.getId(), 90, Duration.ofMinutes(1));

        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with ID: " + request.getConversationId()));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(member -> member.getId().equals(currentUser.getId()));
        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }

        ensureChannelPostingAllowed(conversation, currentUser);
        ensurePrivateMessageAllowed(conversation, currentUser);

        TypingIndicatorEvent event = TypingIndicatorEvent.builder()
                .type("TYPING")
                .conversationId(conversation.getId())
                .userId(currentUser.getId())
                .username(currentUser.getUsername())
                .typing(request.isTyping())
                .updatedAt(LocalDateTime.now())
                .build();

        for (User member : conversation.getMembers()) {
            if (!member.getId().equals(currentUser.getId())) {
                messagingTemplate.convertAndSendToUser(
                        member.getUsername(),
                        "/queue/private",
                        event
                );
            }
        }
    }

    private MessageResponse sendMessageWithUser(MessageRequest request, User currentUser) {
        return sendMessageWithUser(request, currentUser, null, null);
    }

    private MessageResponse sendMessageWithUser(
            MessageRequest request,
            User currentUser,
            String forwardedFromMessageId,
            String forwardedFromSenderUsername
    ) {
        messagePayloadValidator.validate(request);
        rateLimitService.check("message:send", currentUser.getId(), 120, Duration.ofMinutes(1));
        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with ID: " + request.getConversationId()));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(m -> m.getId().equals(currentUser.getId()));

        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }

        String clientMessageId = request.getClientMessageId() != null
                ? request.getClientMessageId().trim()
                : "";
        if (!clientMessageId.isBlank()) {
            Message existing = messageRepository
                    .findByConversationIdAndSenderIdAndClientMessageId(
                            conversation.getId(),
                            currentUser.getId(),
                            clientMessageId
                    )
                    .orElse(null);
            if (existing != null) {
                return mapToMessageResponse(existing);
            }
        }

        ensureChannelPostingAllowed(conversation, currentUser);
        boolean strangerMessage = ensurePrivateMessageAllowed(conversation, currentUser);
        if (strangerMessage) {
            rateLimitService.check(
                    "message:stranger:conversation:" + conversation.getId(),
                    currentUser.getId(),
                    30,
                    Duration.ofMinutes(10)
            );
            rateLimitService.check("message:stranger:daily", currentUser.getId(), 120, Duration.ofDays(1));
        }
        mediaAuthorizationService.authorizeForConversation(request.getAttachments(), currentUser, conversation);
        if (conversation.getDeletedByUsers() != null && !conversation.getDeletedByUsers().isEmpty()) {
            conversation.getDeletedByUsers().clear();
        }

        List<MessageAttachment> attachments = request.getAttachments() != null
                ? request.getAttachments().stream()
                .filter(attachment -> attachment != null && attachment.getUrl() != null && !attachment.getUrl().trim().isEmpty())
                .map(attachment -> MessageAttachment.builder()
                        .url(attachment.getUrl().trim())
                        .type(attachment.getType() != null ? attachment.getType().toUpperCase() : "FILE")
                        .name(attachment.getName())
                        .size(attachment.getSize())
                        .build())
                .toList()
                : List.of();

        String content = request.getContent() != null ? request.getContent().trim() : "";
        if (content.isEmpty() && attachments.isEmpty()) {
            throw new BadRequestException("Message content or attachments are required");
        }

        validateVoiceInviteScope(content, conversation, currentUser);

        MessageType type = attachments.size() > 1 ? MessageType.ALBUM : MessageType.TEXT;
        if (request.getMessageType() != null) {
            try {
                type = MessageType.valueOf(request.getMessageType().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid message type: " + request.getMessageType());
            }
        } else if (attachments.size() == 1) {
            try {
                type = MessageType.valueOf(attachments.get(0).getType());
            } catch (IllegalArgumentException e) {
                type = MessageType.FILE;
            }
        }

        if (type == MessageType.SYSTEM || type == MessageType.POLL) {
            throw new BadRequestException("This message type must be created by its dedicated system flow");
        }
        if (type == MessageType.GIF && !attachments.isEmpty()) {
            throw new BadRequestException("GIF messages cannot contain uploaded attachments");
        }

        String parentId = request.getParentId();
        if (parentId != null) {
            Message parent = messageRepository.findById(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent message not found with ID: " + parentId));
            if (!parent.getConversation().getId().equals(conversation.getId())) {
                throw new BadRequestException("Parent message must be in the same conversation");
            }
            if (parent.getMessageType() == MessageType.SYSTEM || parent.getMessageType() == MessageType.POLL) {
                throw new BadRequestException("Cannot reply to this message type");
            }
        }

        Map<String, Object> metadata = new HashMap<>();
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            metadata.putAll(request.getMetadata());
        }
        if (type == MessageType.GIF) {
            metadata.clear();
            metadata.putAll(giphyMessageMetadataValidator.sanitize(content, request.getMetadata()));
        }
        if (request.getClientMessageId() != null && !request.getClientMessageId().isBlank()) {
            metadata.put("clientMessageId", request.getClientMessageId().trim());
        }
        if (request.getPriority() != null && !request.getPriority().isBlank()) {
            metadata.put("priority", request.getPriority().toUpperCase());
        }
        if (strangerMessage) {
            metadata.put("strangerMessage", true);
            metadata.put("spamRisk", assessStrangerSpamRisk(content, attachments));
        }
        MentionTargets mentionTargets = resolveMentionTargets(content, conversation, currentUser);
        if (mentionTargets.mentionAll()) {
            metadata.put("mentionAll", true);
        }
        if (!mentionTargets.userIds().isEmpty()) {
            metadata.put("mentionedUserIds", new ArrayList<>(mentionTargets.userIds()));
        }
        boolean shouldEnrichLinkPreview = type == MessageType.TEXT
                && !content.isBlank()
                && !Boolean.TRUE.equals(metadata.get("suppressLinkPreview"))
                && !metadata.containsKey("linkPreview")
                && linkPreviewService.containsPreviewableUrl(content);

        Message message = Message.builder()
                .conversation(conversation)
                .conversationId(conversation.getId())
                .sender(currentUser)
                .senderId(currentUser.getId())
                .senderUsername(currentUser.getUsername())
                .content(content)
                .messageType(type)
                .attachments(attachments)
                .parentId(parentId)
                .forwardedFromMessageId(forwardedFromMessageId)
                .forwardedFromSenderUsername(forwardedFromSenderUsername)
                .metadata(metadata)
                .notificationDispatchStatus(MessageNotificationDispatchStatus.PENDING)
                .notificationDispatchNextAttemptAt(LocalDateTime.now())
                .build();
        Integer perMessageSelfDestruct = request.getSelfDestructSeconds();
        if (perMessageSelfDestruct == null && request.getMetadata() != null && request.getMetadata().get("selfDestructSeconds") instanceof Number num) {
            perMessageSelfDestruct = num.intValue();
        }
        int effectiveSelfDestruct = (perMessageSelfDestruct != null && perMessageSelfDestruct > 0)
                ? perMessageSelfDestruct
                : conversation.getSelfDestructSeconds();
        if (effectiveSelfDestruct > 0) {
            message.setExpiresAt(LocalDateTime.now().plusSeconds(effectiveSelfDestruct));
        }

        boolean triggersAiBot = shouldTriggerAiBot(message, conversation);
        if (triggersAiBot) {
            rateLimitService.check("ai:bot", currentUser.getId(), aiBotRateLimit,
                    Duration.ofSeconds(aiBotRateWindowSeconds));
        }

        final Message savedMessage;
        try {
            savedMessage = messageRepository.save(message);
        } catch (DuplicateKeyException duplicate) {
            if (clientMessageId.isBlank()) {
                throw duplicate;
            }
            Message existingMessage = messageRepository
                    .findByConversationIdAndSenderIdAndClientMessageId(
                            conversation.getId(), currentUser.getId(), clientMessageId)
                    .orElseThrow(() -> duplicate);
            return mapToMessageResponse(existingMessage);
        }

        // Create initial SENT status for all other conversation members
        List<MessageStatus> initialStatuses = new ArrayList<>();
        for (User member : conversation.getMembers()) {
            if (!member.getId().equals(currentUser.getId())) {
                MessageStatus statusRecord = MessageStatus.builder()
                        .message(savedMessage)
                        .user(member)
                        .messageId(savedMessage.getId())
                        .conversationId(conversation.getId())
                        .userId(member.getId())
                        .status("SENT")
                        .build();
                initialStatuses.add(statusRecord);
            }
        }
        if (!initialStatuses.isEmpty()) {
            messageStatusRepository.saveAll(initialStatuses);
        }

        // Update conversation's updatedAt timestamp
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        MessageResponse response = mapToMessageResponse(savedMessage);

        // Broadcast to all conversation members over WebSocket IMMEDIATELY (< 15ms)
        for (User member : conversation.getMembers()) {
            messagingTemplate.convertAndSendToUser(
                    member.getUsername(),
                    "/queue/private",
                    response
            );
        }

        if (shouldEnrichLinkPreview) {
            scheduleLinkPreviewEnrichment(savedMessage, content);
        }

        // The message save includes a durable PENDING marker. Dispatch immediately
        // for low latency; an expired lease is resumed after backend restart.
        messageNotificationDispatcher.dispatchNow(savedMessage.getId());

        if (triggersAiBot) {
            aiBotService.answerMentionAsync(conversation, savedMessage, currentUser);
        }

        return response;
    }

    private boolean shouldTriggerAiBot(Message message, Conversation conversation) {
        if (conversation.getType() != ConversationType.GROUP) {
            return false;
        }
        if (message.getMessageType() != MessageType.TEXT || message.getContent() == null) {
            return false;
        }
        String content = message.getContent();
        Matcher idMatcher = QUILL_MENTION_ID_PATTERN.matcher(content);
        while (idMatcher.find()) {
            if ("bot".equalsIgnoreCase(idMatcher.group(1))) {
                return true;
            }
        }
        String plainText = content
                .replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", " ");
        return BOT_MENTION_PATTERN.matcher(plainText).find();
    }

    private MentionTargets resolveMentionTargets(String content, Conversation conversation, User currentUser) {
        if (content == null || content.isBlank()) {
            return new MentionTargets(false, Set.of());
        }

        boolean mentionAll = false;
        Set<String> mentionedUserIds = new HashSet<>();
        Map<String, User> membersById = conversation.getMembers().stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));
        Map<String, User> membersByUsername = conversation.getMembers().stream()
                .collect(Collectors.toMap(
                        member -> member.getUsername().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (first, second) -> first
                ));

        Matcher idMatcher = QUILL_MENTION_ID_PATTERN.matcher(content);
        while (idMatcher.find()) {
            String mentionId = idMatcher.group(1);
            if ("all".equalsIgnoreCase(mentionId)) {
                mentionAll = conversation.getType() == ConversationType.GROUP;
                continue;
            }

            User mentioned = membersById.get(mentionId);
            if (mentioned != null && !mentioned.getId().equals(currentUser.getId())) {
                mentionedUserIds.add(mentioned.getId());
            }
        }

        String plainText = content
                .replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", " ");
        Matcher plainMatcher = PLAIN_MENTION_PATTERN.matcher(plainText);
        while (plainMatcher.find()) {
            String token = plainMatcher.group(2);
            if ("all".equalsIgnoreCase(token)) {
                mentionAll = conversation.getType() == ConversationType.GROUP;
                continue;
            }

            User mentioned = membersByUsername.get(token.toLowerCase(Locale.ROOT));
            if (mentioned != null && !mentioned.getId().equals(currentUser.getId())) {
                mentionedUserIds.add(mentioned.getId());
            }
        }

        if (mentionAll) {
            conversation.getMembers().stream()
                    .filter(member -> !member.getId().equals(currentUser.getId()))
                    .map(User::getId)
                    .forEach(mentionedUserIds::add);
        }

        return new MentionTargets(mentionAll, mentionedUserIds);
    }

    @SuppressWarnings("unchecked")
    private boolean isMemberMentioned(Message message, User member) {
        if (message.getMetadata() == null || member == null) {
            return false;
        }

        if (Boolean.TRUE.equals(message.getMetadata().get("mentionAll"))) {
            return true;
        }

        Object rawMentionedIds = message.getMetadata().get("mentionedUserIds");
        if (rawMentionedIds instanceof Collection<?> mentionedIds) {
            return mentionedIds.stream().anyMatch(id -> member.getId().equals(String.valueOf(id)));
        }

        return false;
    }

    private record MentionTargets(boolean mentionAll, Set<String> userIds) {}

    private void validateVoiceInviteScope(String content, Conversation targetConversation, User currentUser) {
        Matcher matcher = VOICE_INVITE_PATTERN.matcher(content);
        if (!matcher.find()) return;

        String voiceChannelId = matcher.group(1);
        String claimedGroupId = matcher.group(2);
        Channel voiceChannel = channelRepository.findById(voiceChannelId)
                .orElseThrow(() -> new BadRequestException("Voice channel invitation is invalid"));
        if (voiceChannel.getType() != iuh.fit.se.nextalk_be.entity.ChannelType.VOICE || voiceChannel.getGroup() == null) {
            throw new BadRequestException("Voice channel invitation is invalid");
        }

        String sourceGroupId = voiceChannel.getGroup().getId();
        if (!sourceGroupId.equals(claimedGroupId)
                || !groupMemberRepository.existsByGroupIdAndUserId(sourceGroupId, currentUser.getId())
                || !voiceChannelService.getChannelMembers(voiceChannelId).contains(currentUser.getId())) {
            throw new BadRequestException("You must be in this voice channel to send an invitation");
        }

        Optional<Channel> targetChannel = channelRepository.findByConversationId(targetConversation.getId());
        if (targetChannel.isPresent()) {
            Channel channel = targetChannel.get();
            if (channel.getGroup() == null || !sourceGroupId.equals(channel.getGroup().getId())) {
                throw new BadRequestException("Voice invitations cannot be sent to another group");
            }
            if (channel.getType() == iuh.fit.se.nextalk_be.entity.ChannelType.VOICE) {
                throw new BadRequestException("Voice invitations cannot be sent to a voice channel");
            }
            return;
        }

        if (targetConversation.getType() != ConversationType.PRIVATE
                || targetConversation.getMembers().stream().anyMatch(member ->
                    !groupMemberRepository.existsByGroupIdAndUserId(sourceGroupId, member.getId()))) {
            throw new BadRequestException("Voice invitations can only be sent to members of the same group");
        }
    }

    private boolean ensurePrivateMessageAllowed(Conversation conversation, User currentUser) {
        if (conversation.getType() != ConversationType.PRIVATE) {
            return false;
        }

        User otherMember = conversation.getMembers().stream()
                .filter(member -> !member.getId().equals(currentUser.getId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Private conversation must have another member"));

        if (userBlockRepository.existsBetweenUsers(currentUser.getId(), otherMember.getId())) {
            throw new BadRequestException("You cannot message this user because one of you has blocked the other.");
        }

        boolean areFriends = friendshipRepository.findFriendshipBetweenUsers(currentUser.getId(), otherMember.getId())
                .filter(friendship -> friendship.getStatus() == FriendshipStatus.ACCEPTED)
                .isPresent();
        if (otherMember.isBlockStrangerMessages() && !areFriends) {
            throw new BadRequestException("Người dùng này chỉ nhận tin nhắn từ bạn bè.");
        }
        return !areFriends;
    }

    private String assessStrangerSpamRisk(String content, List<MessageAttachment> attachments) {
        if (content == null || content.isBlank()) {
            return "LOW";
        }
        Matcher linkMatcher = WEB_LINK_PATTERN.matcher(content);
        int linkCount = 0;
        while (linkMatcher.find()) {
            linkCount++;
        }
        boolean suspiciousLink = SUSPICIOUS_LINK_PATTERN.matcher(content).find();
        boolean attachmentBurst = attachments != null && attachments.size() >= 5;
        return linkCount >= 3 || suspiciousLink || attachmentBurst ? "MEDIUM" : "LOW";
    }

    // @Transactional(readOnly = true)
    public Page<MessageResponse> getConversationMessages(String conversationId, Pageable pageable) {
        long lookupStartedAt = System.nanoTime();
        User currentUser = userService.getCurrentAuthenticatedUser();

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with ID: " + conversationId));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(m -> m.getId().equals(currentUser.getId()));

        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }

        long queryStartedAt = System.nanoTime();
        org.springframework.data.domain.Slice<Message> messages = messageRepository.findVisibleConversationMessages(
                conversationId, currentUser.getId(), pageable
        );
        long mappingStartedAt = System.nanoTime();
        List<MessageResponse> content = mapMessagesToResponses(messages.getContent());
        content = content.stream()
                .filter(message -> message.isRecalled() || message.getExpiresAt() == null || message.getExpiresAt().isAfter(LocalDateTime.now()))
                .toList();
        long estimatedTotal = pageable.getOffset() + content.size() + (messages.hasNext() ? 1 : 0);
        log.debug("Message history timing conversation={} lookupMs={} queryMs={} mapMs={} count={}", conversationId,
                (queryStartedAt - lookupStartedAt) / 1_000_000,
                (mappingStartedAt - queryStartedAt) / 1_000_000,
                (System.nanoTime() - mappingStartedAt) / 1_000_000, content.size());
        return new PageImpl<>(content, pageable, estimatedTotal);
    }

    @Override
    public MessageCursorPageResponse getConversationMessageHistory(String conversationId, String cursor, int limit) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        ensureConversationMember(conversationId, currentUser);
        return queryConversationHistory(conversationId, currentUser.getId(), cursor, limit);
    }

    @Override
    public MessageAroundResponse getMessagesAround(String conversationId, String messageId, int limit) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        ensureConversationMember(conversationId, currentUser);

        Message anchor = messageRepository.findById(messageId)
                .filter(message -> conversationId.equals(messageConversationId(message)))
                .filter(message -> isMessageVisibleTo(message, currentUser.getId(), LocalDateTime.now()))
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));

        int safeLimit = Math.min(31, Math.max(5, limit));
        int olderLimit = safeLimit / 2;
        int newerLimit = safeLimit - olderLimit - 1;
        MessageCursorCodec.Cursor anchorCursor = new MessageCursorCodec.Cursor(anchor.getCreatedAt(), anchor.getId());
        Criteria visibility = visibleConversationCriteria(conversationId, currentUser.getId(), LocalDateTime.now());

        Query olderQuery = new Query(new Criteria().andOperator(
                visibility,
                cursorBoundary(anchorCursor, true)))
                .with(stableMessageSort(Sort.Direction.DESC))
                .limit(olderLimit + 1);
        List<Message> older = new ArrayList<>(mongoTemplate.find(olderQuery, Message.class));
        boolean hasOlder = older.size() > olderLimit;
        if (hasOlder) older.remove(older.size() - 1);

        Query newerQuery = new Query(new Criteria().andOperator(
                visibleConversationCriteria(conversationId, currentUser.getId(), LocalDateTime.now()),
                cursorBoundary(anchorCursor, false)))
                .with(stableMessageSort(Sort.Direction.ASC))
                .limit(newerLimit + 1);
        List<Message> newer = new ArrayList<>(mongoTemplate.find(newerQuery, Message.class));
        boolean hasNewer = newer.size() > newerLimit;
        if (hasNewer) newer.remove(newer.size() - 1);
        Collections.reverse(newer);

        List<Message> window = new ArrayList<>(newer.size() + older.size() + 1);
        window.addAll(newer);
        window.add(anchor);
        window.addAll(older);
        Message oldest = window.get(window.size() - 1);

        return MessageAroundResponse.builder()
                .items(mapMessagesToResponses(window))
                .anchorMessageId(anchor.getId())
                .nextCursor(hasOlder ? MessageCursorCodec.encode(oldest.getCreatedAt(), oldest.getId()) : null)
                .hasMore(hasOlder)
                .hasNewer(hasNewer)
                .build();
    }

    private MessageCursorPageResponse queryConversationHistory(
            String conversationId,
            String currentUserId,
            String encodedCursor,
            int requestedLimit
    ) {
        int safeLimit = Math.min(50, Math.max(1, requestedLimit));
        MessageCursorCodec.Cursor cursor = MessageCursorCodec.decode(encodedCursor);
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(visibleConversationCriteria(conversationId, currentUserId, LocalDateTime.now()));
        if (cursor != null) criteria.add(cursorBoundary(cursor, true));

        Query query = new Query(new Criteria().andOperator(criteria.toArray(Criteria[]::new)))
                .with(stableMessageSort(Sort.Direction.DESC))
                .limit(safeLimit + 1);
        List<Message> messages = new ArrayList<>(mongoTemplate.find(query, Message.class));
        boolean hasMore = messages.size() > safeLimit;
        if (hasMore) messages.remove(messages.size() - 1);
        Message last = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        return MessageCursorPageResponse.builder()
                .items(mapMessagesToResponses(messages))
                .nextCursor(hasMore && last != null
                        ? MessageCursorCodec.encode(last.getCreatedAt(), last.getId())
                        : null)
                .hasMore(hasMore)
                .build();
    }

    private void ensureConversationMember(String conversationId, User currentUser) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        boolean member = conversation.getMembers().stream()
                .anyMatch(candidate -> candidate.getId().equals(currentUser.getId()));
        if (!member) throw new BadRequestException("You are not a member of this conversation");
    }

    private Criteria visibleConversationCriteria(String conversationId, String currentUserId, LocalDateTime now) {
        return new Criteria().andOperator(
                new Criteria().orOperator(
                        Criteria.where("conversationId").is(conversationId),
                        Criteria.where("conversation").is(conversationId)),
                Criteria.where("deletedByUsers").ne(currentUserId),
                new Criteria().orOperator(
                        Criteria.where("isRecalled").is(true),
                        Criteria.where("expiresAt").exists(false),
                        Criteria.where("expiresAt").is(null),
                        Criteria.where("expiresAt").gt(now)));
    }

    private Criteria cursorBoundary(MessageCursorCodec.Cursor cursor, boolean older) {
        Criteria timestamp = older
                ? Criteria.where("createdAt").lt(cursor.createdAt())
                : Criteria.where("createdAt").gt(cursor.createdAt());
        Criteria messageId = older
                ? Criteria.where("_id").lt(cursor.messageId())
                : Criteria.where("_id").gt(cursor.messageId());
        return new Criteria().orOperator(
                timestamp,
                new Criteria().andOperator(
                        Criteria.where("createdAt").is(cursor.createdAt()),
                        messageId));
    }

    private Sort stableMessageSort(Sort.Direction direction) {
        return Sort.by(
                new Sort.Order(direction, "createdAt"),
                new Sort.Order(direction, "_id"));
    }

    private String messageConversationId(Message message) {
        return message.getConversationId() != null
                ? message.getConversationId()
                : message.getConversation() != null ? message.getConversation().getId() : null;
    }

    private boolean isMessageVisibleTo(Message message, String currentUserId, LocalDateTime now) {
        boolean deleted = message.getDeletedByUsers() != null
                && message.getDeletedByUsers().contains(currentUserId);
        boolean available = message.isRecalled()
                || message.getExpiresAt() == null
                || message.getExpiresAt().isAfter(now);
        return !deleted && available;
    }

    @Override
    public MessageSyncResponse syncConversationMessages(String conversationId, LocalDateTime since, int limit) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with ID: " + conversationId));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(member -> member.getId().equals(currentUser.getId()));
        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }

        LocalDateTime queryUntil = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime cursor = queryUntil.minus(1, ChronoUnit.MILLIS);
        if (since != null && since.isAfter(cursor)) {
            cursor = since;
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        if (since == null || since.isAfter(queryUntil)) {
            return buildFullMessageSnapshot(conversationId, currentUser.getId(), cursor, safeLimit);
        }

        Pageable changePage = org.springframework.data.domain.PageRequest.of(0, safeLimit + 1);
        List<Message> changedMessages = messageRepository.findConversationChanges(
                conversationId, since, queryUntil, changePage);
        List<MessageStatus> changedStatuses = messageStatusRepository
                .findConversationStatusChanges(
                        conversationId, since, queryUntil, changePage);

        if (changedMessages.size() > safeLimit || changedStatuses.size() > safeLimit) {
            return buildFullMessageSnapshot(conversationId, currentUser.getId(), cursor, safeLimit);
        }

        LinkedHashMap<String, Message> changesById = new LinkedHashMap<>();
        changedMessages.forEach(message -> changesById.put(message.getId(), message));
        Set<String> statusMessageIds = changedStatuses.stream()
                .map(status -> status.getMessageId() != null
                        ? status.getMessageId()
                        : status.getMessage() != null ? status.getMessage().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!statusMessageIds.isEmpty()) {
            messageRepository.findAllById(statusMessageIds).forEach(message -> {
                String messageConversationId = message.getConversationId() != null
                        ? message.getConversationId()
                        : message.getConversation() != null ? message.getConversation().getId() : null;
                if (conversationId.equals(messageConversationId)) {
                    changesById.put(message.getId(), message);
                }
            });
        }

        if (changesById.size() > safeLimit) {
            return buildFullMessageSnapshot(conversationId, currentUser.getId(), cursor, safeLimit);
        }

        List<String> deletedMessageIds = changesById.values().stream()
                .filter(message -> message.getDeletedByUsers() != null
                        && message.getDeletedByUsers().contains(currentUser.getId()))
                .map(Message::getId)
                .toList();
        LocalDateTime now = LocalDateTime.now();
        List<Message> visibleChanges = changesById.values().stream()
                .filter(message -> message.getDeletedByUsers() == null
                        || !message.getDeletedByUsers().contains(currentUser.getId()))
                .filter(message -> message.isRecalled() || message.getExpiresAt() == null || message.getExpiresAt().isAfter(now))
                .sorted(Comparator.comparing(
                        Message::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return MessageSyncResponse.builder()
                .messages(mapMessagesToResponses(visibleChanges))
                .deletedMessageIds(deletedMessageIds)
                .cursor(cursor)
                .fullSnapshot(false)
                .build();
    }

    private MessageSyncResponse buildFullMessageSnapshot(
            String conversationId,
            String currentUserId,
            LocalDateTime cursor,
            int requestedLimit
    ) {
        int snapshotSize = Math.max(1, Math.min(requestedLimit, 50));
        MessageCursorPageResponse history = queryConversationHistory(
                conversationId, currentUserId, null, snapshotSize);
        return MessageSyncResponse.builder()
                .messages(history.getItems())
                .deletedMessageIds(List.of())
                .cursor(cursor)
                .fullSnapshot(true)
                .historyNextCursor(history.getNextCursor())
                .historyHasMore(history.isHasMore())
                .build();
    }

    // @Transactional
    public void markConversationMessagesAsDelivered(String conversationId, String username) {
        User user = userRepository.findByEmail(username)
                .or(() -> userRepository.findByUsername(username))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(m -> m.getId().equals(user.getId()));
        if (!isMember) {
            throw new BadRequestException("User is not a member of this conversation");
        }

        List<MessageStatus> statusesToUpdate = messageStatusRepository.findAllByConversationIdAndUserIdAndStatusIn(
                conversationId, user.getId(), List.of("SENT"));

        if (!statusesToUpdate.isEmpty()) {
            for (MessageStatus status : statusesToUpdate) {
                status.setStatus("DELIVERED");
            }
            messageStatusRepository.saveAll(statusesToUpdate);

            MessageStatusUpdateResponse updateResponse = MessageStatusUpdateResponse.builder()
                    .conversationId(conversationId)
                    .userId(user.getId())
                    .username(user.getUsername())
                    .status("DELIVERED")
                    .updatedAt(LocalDateTime.now())
                    .build();

            for (User member : conversation.getMembers()) {
                if (!member.getId().equals(user.getId())) {
                    messagingTemplate.convertAndSendToUser(
                            member.getUsername(),
                            "/queue/private",
                            updateResponse
                    );
                }
            }
        }
    }

    // @Transactional
    public void markConversationMessagesAsSeen(String conversationId, String username) {
        User user = userRepository.findByEmail(username)
                .or(() -> userRepository.findByUsername(username))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(m -> m.getId().equals(user.getId()));
        if (!isMember) {
            throw new BadRequestException("User is not a member of this conversation");
        }

        conversationUnreadMarkerRepository.deleteByUserIdAndConversationId(user.getId(), conversationId);

        List<MessageStatus> statusesToUpdate = messageStatusRepository.findAllByConversationIdAndUserIdAndStatusIn(
                conversationId, user.getId(), List.of("SENT", "DELIVERED"));

        if (!statusesToUpdate.isEmpty()) {
            for (MessageStatus status : statusesToUpdate) {
                status.setStatus("SEEN");
            }
            messageStatusRepository.saveAll(statusesToUpdate);

            MessageStatusUpdateResponse updateResponse = MessageStatusUpdateResponse.builder()
                    .conversationId(conversationId)
                    .userId(user.getId())
                    .username(user.getUsername())
                    .status("SEEN")
                    .updatedAt(LocalDateTime.now())
                    .build();

            for (User member : conversation.getMembers()) {
                // Also notify the reader's own WebSocket sessions so unread state
                // is cleared immediately on their other signed-in devices.
                messagingTemplate.convertAndSendToUser(
                        member.getUsername(),
                        "/queue/private",
                        updateResponse
                );
            }
        }
    }

    @Override
    public Map<String, Long> getUnreadCounts(String username) {
        User user = userRepository.findByEmail(username)
                .or(() -> userRepository.findByUsername(username))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Map<String, Long> counts = messageStatusRepository.countUnreadByConversation(
                        user.getId(), List.of("SENT", "DELIVERED"))
                .stream()
                .collect(Collectors.toMap(
                        MessageStatusRepository.UnreadCountResult::conversationId,
                        MessageStatusRepository.UnreadCountResult::count));
        conversationUnreadMarkerRepository.findAllByUserId(user.getId())
                .forEach(marker -> counts.merge(marker.getConversationId(), 1L, Math::max));
        return counts;
    }

    @Override
    public ConversationUnreadResponse markConversationAsUnread(String conversationId, String username) {
        User user = userRepository.findByEmail(username)
                .or(() -> userRepository.findByUsername(username))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        boolean isMember = conversation.getMembers().stream()
                .anyMatch(member -> member.getId().equals(user.getId()));
        if (!isMember) {
            throw new BadRequestException("User is not a member of this conversation");
        }

        Message latestMessage = messageRepository
                .findVisibleConversationMessages(conversationId, user.getId(), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Conversation has no message to mark as unread"));

        ConversationUnreadMarker marker = conversationUnreadMarkerRepository
                .findByUserIdAndConversationId(user.getId(), conversationId)
                .orElseGet(ConversationUnreadMarker::new);
        marker.setUserId(user.getId());
        marker.setConversationId(conversationId);
        marker.setMessageId(latestMessage.getId());
        marker = conversationUnreadMarkerRepository.save(marker);

        return ConversationUnreadResponse.builder()
                .conversationId(conversationId)
                .messageId(marker.getMessageId())
                .unreadCount(1)
                .build();
    }

    @Override
    public MessageDeliveryDetailsResponse getMessageDeliveryDetails(String messageId, String status, int page, int size) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        Conversation conversation = message.getConversation();
        if (conversation == null && message.getConversationId() != null) {
            conversation = conversationRepository.findById(message.getConversationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        }
        if (conversation == null || conversation.getMembers().stream().noneMatch(member -> member.getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("You are not a member of this conversation");
        }
        String senderId = message.getSenderId() != null
                ? message.getSenderId()
                : message.getSender() != null ? message.getSender().getId() : null;
        if (!currentUser.getId().equals(senderId)) {
            throw new AccessDeniedException("Only the sender can view message delivery details");
        }
        if (message.getDeletedByUsers() != null && message.getDeletedByUsers().contains(currentUser.getId())) {
            throw new ResourceNotFoundException("Message not found");
        }

        String normalizedStatus = status == null ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "SEEN", "DELIVERED", "SENT").contains(normalizedStatus)) {
            throw new BadRequestException("Unsupported delivery status filter");
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<MessageStatus> statusPage = "ALL".equals(normalizedStatus)
                ? messageStatusRepository.findByMessageId(messageId, pageable)
                : messageStatusRepository.findByMessageIdAndStatus(messageId, normalizedStatus, pageable);

        List<String> userIds = statusPage.getContent().stream()
                .map(record -> record.getUserId() != null
                        ? record.getUserId()
                        : record.getUser() != null ? record.getUser().getId() : null)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<MessageDeliveryParticipantResponse> items = statusPage.getContent().stream()
                .map(record -> {
                    String userId = record.getUserId() != null
                            ? record.getUserId()
                            : record.getUser() != null ? record.getUser().getId() : null;
                    User recipient = userId == null ? null : usersById.get(userId);
                    return MessageDeliveryParticipantResponse.builder()
                            .userId(userId)
                            .username(recipient != null
                                    ? recipient.getUsername()
                                    : record.getUser() != null ? record.getUser().getUsername() : "Thành viên")
                            .avatarUrl(recipient != null ? recipient.getAvatarUrl() : null)
                            .status(record.getStatus())
                            .updatedAt(record.getUpdatedAt() != null ? record.getUpdatedAt() : record.getCreatedAt())
                            .build();
                })
                .toList();

        long seenCount = messageStatusRepository.countByMessageIdAndStatus(messageId, "SEEN");
        long deliveredCount = messageStatusRepository.countByMessageIdAndStatus(messageId, "DELIVERED");
        long sentCount = messageStatusRepository.countByMessageIdAndStatus(messageId, "SENT");
        return MessageDeliveryDetailsResponse.builder()
                .messageId(messageId)
                .seenCount(seenCount)
                .deliveredCount(deliveredCount)
                .sentCount(sentCount)
                .totalRecipients(seenCount + deliveredCount + sentCount)
                .items(items)
                .page(safePage)
                .size(safeSize)
                .totalElements(statusPage.getTotalElements())
                .hasMore(statusPage.hasNext())
                .build();
    }

    private MessageResponse mapToMessageResponse(Message message) {
        List<MessageStatus> statusRecords = messageStatusRepository.findAllByMessageId(message.getId());
        return mapToMessageResponseWithStatuses(message, statusRecords);
    }

    private MessageResponse mapToMessageResponseWithStatuses(Message message, List<MessageStatus> statusRecords) {
        lockExpiredPollIfNeeded(message);

        List<MessageStatusResponse> statusResponses = statusRecords.stream()
                .map(status -> MessageStatusResponse.builder()
                        .userId(status.getUser().getId())
                        .username(status.getUser().getUsername())
                        .status(status.getStatus())
                        .updatedAt(status.getUpdatedAt() != null ? status.getUpdatedAt() : status.getCreatedAt() != null ? status.getCreatedAt() : LocalDateTime.now())
                        .build())
                .toList();

        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderId(message.getSender().getId())
                .senderUsername(message.getSender().getUsername())
                .clientMessageId(getClientMessageId(message))
                .content(message.getContent())
                .messageType(message.getMessageType().name())
                .attachments(message.getAttachments() != null ? message.getAttachments() : new ArrayList<>())
                .createdAt(message.getCreatedAt() != null ? message.getCreatedAt() : LocalDateTime.now())
                .statuses(statusResponses)
                .parentId(message.getParentId())
                .forwardedFromMessageId(message.getForwardedFromMessageId())
                .forwardedFromSenderUsername(message.getForwardedFromSenderUsername())
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

    private MessageResponse mapToMessageResponseOptimized(
            Message message,
            List<MessageStatus> statusRecords,
            Map<String, String> usernameMap
    ) {
        lockExpiredPollIfNeeded(message);

        List<MessageStatusResponse> statusResponses = statusRecords.stream()
                .map(status -> {
                    String userId = status.getUserId() != null ? status.getUserId() : status.getUser().getId();
                    String username = usernameMap.getOrDefault(userId, "unknown");
                    return MessageStatusResponse.builder()
                            .userId(userId)
                            .username(username)
                            .status(status.getStatus())
                            .updatedAt(status.getUpdatedAt() != null ? status.getUpdatedAt() : status.getCreatedAt() != null ? status.getCreatedAt() : LocalDateTime.now())
                            .build();
                })
                .toList();

        String senderId = message.getSenderId() != null ? message.getSenderId() : message.getSender().getId();
        String senderUsername = message.getSenderUsername() != null
                ? message.getSenderUsername()
                : usernameMap.getOrDefault(senderId, "unknown");

        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId() != null ? message.getConversationId() : message.getConversation().getId())
                .senderId(senderId)
                .senderUsername(senderUsername)
                .clientMessageId(getClientMessageId(message))
                .content(message.getContent())
                .messageType(message.getMessageType().name())
                .attachments(message.getAttachments() != null ? message.getAttachments() : new ArrayList<>())
                .createdAt(message.getCreatedAt() != null ? message.getCreatedAt() : LocalDateTime.now())
                .statuses(statusResponses)
                .parentId(message.getParentId())
                .forwardedFromMessageId(message.getForwardedFromMessageId())
                .forwardedFromSenderUsername(message.getForwardedFromSenderUsername())
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

    private String getClientMessageId(Message message) {
        if (message.getMetadata() == null) {
            return null;
        }
        Object value = message.getMetadata().get("clientMessageId");
        return value instanceof String clientMessageId && !clientMessageId.isBlank()
                ? clientMessageId
                : null;
    }

    private List<MessageResponse> mapMessagesToResponses(List<Message> messageList) {
        if (messageList == null || messageList.isEmpty()) {
            return Collections.emptyList();
        }

        long statusStartedAt = System.nanoTime();
        List<String> messageIds = messageList.stream().map(Message::getId).toList();
        List<MessageStatus> allStatuses = messageStatusRepository.findAllByMessageIdIn(messageIds);
        long usersStartedAt = System.nanoTime();

        // Collect all user IDs to batch fetch usernames
        Set<String> userIds = new HashSet<>();
        for (Message msg : messageList) {
            if (msg.getSenderId() != null) userIds.add(msg.getSenderId());
            else if (msg.getSender() != null) userIds.add(msg.getSender().getId());
        }
        for (MessageStatus status : allStatuses) {
            if (status.getUserId() != null) userIds.add(status.getUserId());
            else if (status.getUser() != null) userIds.add(status.getUser().getId());
        }

        List<User> users = userRepository.findAllById(userIds);
        long dtoStartedAt = System.nanoTime();
        Map<String, String> usernameMap = new HashMap<>();
        for (User u : users) {
            usernameMap.put(u.getId(), u.getUsername());
        }

        // Group statuses by Message ID
        Map<String, List<MessageStatus>> statusMap = new HashMap<>();
        for (MessageStatus status : allStatuses) {
            String messageId = status.getMessageId() != null ? status.getMessageId()
                    : status.getMessage() != null ? status.getMessage().getId() : null;
            if (messageId != null) statusMap.computeIfAbsent(messageId, k -> new ArrayList<>()).add(status);
        }

        List<MessageResponse> responses = new ArrayList<>();
        for (Message message : messageList) {
            responses.add(mapToMessageResponseOptimized(
                    message,
                    statusMap.getOrDefault(message.getId(), List.of()),
                    usernameMap
            ));
        }
        log.debug("Message mapping timing statusMs={} usersMs={} dtoMs={} messages={} statuses={} users={}",
                (usersStartedAt - statusStartedAt) / 1_000_000,
                (dtoStartedAt - usersStartedAt) / 1_000_000,
                (System.nanoTime() - dtoStartedAt) / 1_000_000,
                messageList.size(), allStatuses.size(), users.size());
        return responses;
    }

    private void broadcastMessageUpdate(Conversation conversation, MessageResponse response) {
        for (User member : conversation.getMembers()) {
            messagingTemplate.convertAndSendToUser(
                    member.getUsername(),
                    "/queue/private",
                    response
            );
        }
    }

    private void createAndBroadcastSystemMessage(Conversation conversation, User actor, String content) {
        Message systemMessage = Message.builder()
                .conversation(conversation)
                .sender(actor)
                .content(content)
                .messageType(MessageType.SYSTEM)
                .build();

        Message savedSystemMessage = messageRepository.save(systemMessage);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        broadcastMessageUpdate(conversation, mapToMessageResponse(savedSystemMessage));
    }

    public void createAndBroadcastCallHistoryMessage(
            Conversation conversation,
            User actor,
            String content,
            Map<String, Object> metadata
    ) {
        Message systemMessage = Message.builder()
                .conversation(conversation)
                .sender(actor)
                .content(content)
                .messageType(MessageType.SYSTEM)
                .metadata(metadata != null ? metadata : Map.of())
                .build();

        Message savedSystemMessage = messageRepository.save(systemMessage);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        broadcastMessageUpdate(conversation, mapToMessageResponse(savedSystemMessage));
    }

    private void scheduleLinkPreviewEnrichment(Message savedMessage, String expectedContent) {
        String messageId = savedMessage.getId();
        linkPreviewEnrichmentScheduler.submit(() -> {
            try {
                messageLinkPreviewEnricher.enrich(messageId, expectedContent).ifPresent(enrichedMessage -> {
                    MessageResponse enrichedResponse = mapToMessageResponse(enrichedMessage);
                    Map<String, Object> responseMetadata = enrichedResponse.getMetadata() == null
                            ? new HashMap<>()
                            : new HashMap<>(enrichedResponse.getMetadata());
                    responseMetadata.put("realtimeEvent", LINK_PREVIEW_UPDATED_EVENT);
                    enrichedResponse.setMetadata(responseMetadata);
                    broadcastMessageUpdate(enrichedMessage.getConversation(), enrichedResponse);
                });
            } catch (Exception exception) {
                // Do not log message content or URL; both are private chat data.
                log.debug("Link preview enrichment failed messageId={} ({})",
                        messageId, exception.getClass().getSimpleName());
            }
        });
    }

    @Override
    public MessageResponse createAndBroadcastSystemMessage(
            Conversation conversation,
            User actor,
            String content,
            Map<String, Object> metadata
    ) {
        Message systemMessage = Message.builder()
                .conversation(conversation)
                .sender(actor)
                .content(content)
                .messageType(MessageType.SYSTEM)
                .metadata(metadata != null ? metadata : Map.of())
                .build();
        Message saved = messageRepository.save(systemMessage);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        MessageResponse response = mapToMessageResponse(saved);
        broadcastMessageUpdate(conversation, response);
        return response;
    }

    @Override
    public MessageResponse updateAndBroadcastSystemMessage(
            String messageId,
            String content,
            Map<String, Object> metadata
    ) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("System message not found"));
        if (message.getMessageType() != MessageType.SYSTEM) {
            throw new BadRequestException("Only system messages can be updated");
        }
        message.setContent(content);
        message.setMetadata(metadata != null ? metadata : Map.of());
        Message saved = messageRepository.save(message);
        MessageResponse response = mapToMessageResponse(saved);
        broadcastMessageUpdate(saved.getConversation(), response);
        return response;
    }

    public MessageResponse createPoll(CreatePollRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with ID: " + request.getConversationId()));

        if (conversation.getType() != ConversationType.GROUP) {
            throw new BadRequestException("Polls can only be created in group conversations");
        }
        ensureConversationMember(conversation, currentUser);
        if (!canModerateGroup(conversation, currentUser)) {
            throw new BadRequestException("Only the group leader or deputy can create polls");
        }

        List<String> optionTexts = request.getOptions().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .distinct()
                .toList();

        if (optionTexts.size() < 2) {
            throw new BadRequestException("A poll must have at least two options");
        }

        List<Map<String, Object>> options = optionTexts.stream()
                .map(text -> createPollOptionMetadata(text, currentUser))
                .toList();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("systemType", "POLL");
        metadata.put("question", request.getQuestion().trim());
        metadata.put("allowMultiple", request.isAllowMultiple());
        metadata.put("allowAddOptions", request.isAllowAddOptions());
        metadata.put("anonymous", request.isAnonymous());
        metadata.put("locked", false);
        metadata.put("expiresAt", normalizePollExpiresAt(request.getExpiresAt()));
        metadata.put("creatorId", currentUser.getId());
        metadata.put("creatorName", currentUser.getUsername());
        metadata.put("options", options);

        Message poll = Message.builder()
                .conversation(conversation)
                .sender(currentUser)
                .content(request.getQuestion().trim())
                .messageType(MessageType.POLL)
                .metadata(metadata)
                .build();

        Message savedPoll = messageRepository.save(poll);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        MessageResponse response = mapToMessageResponse(savedPoll);
        broadcastMessageUpdate(conversation, response);
        return response;
    }

    public MessageResponse votePoll(String messageId, PollVoteRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        for (int attempt = 0; attempt < MAX_ATOMIC_MUTATION_ATTEMPTS; attempt++) {
            Message poll = getPollMessageForMember(messageId, currentUser);
            Map<String, Object> metadata = mutableMetadata(poll);
            ensurePollOpen(poll, metadata);

            boolean allowMultiple = Boolean.TRUE.equals(metadata.get("allowMultiple"));
            List<Map<String, Object>> options = mutablePollOptions(metadata);
            Map<String, Object> targetOption = options.stream()
                    .filter(option -> request.getOptionId().equals(option.get("id")))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Poll option not found"));

            boolean alreadyVoted = getVoterIds(targetOption).contains(currentUser.getId());
            if (!allowMultiple && !alreadyVoted) {
                for (Map<String, Object> option : options) {
                    removeVoter(option, currentUser.getId());
                }
            }

            if (alreadyVoted) {
                removeVoter(targetOption, currentUser.getId());
            } else {
                addVoter(targetOption, currentUser);
            }

            metadata.put("options", options);
            Message savedPoll = compareAndSetPollMetadata(poll, metadata);
            if (savedPoll != null) {
                MessageResponse response = mapToMessageResponse(savedPoll);
                broadcastMessageUpdate(savedPoll.getConversation(), response);
                return response;
            }
        }
        throw new ConflictException("Poll was updated concurrently; please retry");
    }

    public MessageResponse addPollOption(String messageId, AddPollOptionRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        String text = request.getText() != null ? request.getText().trim() : "";
        if (text.isEmpty()) {
            throw new BadRequestException("Option text is required");
        }
        for (int attempt = 0; attempt < MAX_ATOMIC_MUTATION_ATTEMPTS; attempt++) {
            Message poll = getPollMessageForMember(messageId, currentUser);
            Map<String, Object> metadata = mutableMetadata(poll);
            ensurePollOpen(poll, metadata);

            if (!Boolean.TRUE.equals(metadata.get("allowAddOptions")) && !canManagePoll(poll, currentUser)) {
                throw new BadRequestException("This poll does not allow members to add options");
            }

            List<Map<String, Object>> options = mutablePollOptions(metadata);
            boolean exists = options.stream().anyMatch(option -> text.equalsIgnoreCase(String.valueOf(option.get("text"))));
            if (exists) {
                throw new BadRequestException("Poll option already exists");
            }

            options.add(createPollOptionMetadata(text, currentUser));
            metadata.put("options", options);
            Message savedPoll = compareAndSetPollMetadata(poll, metadata);
            if (savedPoll != null) {
                MessageResponse response = mapToMessageResponse(savedPoll);
                broadcastMessageUpdate(savedPoll.getConversation(), response);
                return response;
            }
        }
        throw new ConflictException("Poll was updated concurrently; please retry");
    }

    public MessageResponse lockPoll(String messageId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        for (int attempt = 0; attempt < MAX_ATOMIC_MUTATION_ATTEMPTS; attempt++) {
            Message poll = getPollMessageForMember(messageId, currentUser);
            if (!canManagePoll(poll, currentUser)) {
                throw new BadRequestException("Only the poll creator or group admins can lock this poll");
            }
            Map<String, Object> metadata = mutableMetadata(poll);
            metadata.put("locked", true);
            metadata.put("lockedAt", LocalDateTime.now().toString());
            Message savedPoll = compareAndSetPollMetadata(poll, metadata);
            if (savedPoll != null) {
                MessageResponse response = mapToMessageResponse(savedPoll);
                broadcastMessageUpdate(savedPoll.getConversation(), response);
                return response;
            }
        }
        throw new ConflictException("Poll was updated concurrently; please retry");
    }

    public MessageResponse deletePoll(String messageId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        for (int attempt = 0; attempt < MAX_ATOMIC_MUTATION_ATTEMPTS; attempt++) {
            Message poll = getPollMessageForMember(messageId, currentUser);
            if (!canManagePoll(poll, currentUser)) {
                throw new BadRequestException("Only the poll creator or group admins can delete this poll");
            }
            Update update = new Update()
                    .set("isRecalled", true)
                    .set("isPinned", false)
                    .unset("pinnedAt")
                    .set("content", "Bình chọn đã bị xóa");
            Message savedPoll = compareAndSetMutation(poll, update);
            if (savedPoll != null) {
                MessageResponse response = mapToMessageResponse(savedPoll);
                broadcastMessageUpdate(savedPoll.getConversation(), response);
                return response;
            }
        }
        throw new ConflictException("Poll was updated concurrently; please retry");
    }

    private Map<String, Object> createPollOptionMetadata(String text, User creator) {
        Map<String, Object> option = new HashMap<>();
        option.put("id", UUID.randomUUID().toString());
        option.put("text", text);
        option.put("createdById", creator.getId());
        option.put("createdByName", creator.getUsername());
        option.put("voterIds", new ArrayList<String>());
        option.put("voters", new ArrayList<Map<String, Object>>());
        return option;
    }

    private Message getPollMessageForMember(String messageId, User currentUser) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));
        if (message.getMessageType() != MessageType.POLL) {
            throw new BadRequestException("Message is not a poll");
        }
        ensureConversationMember(message.getConversation(), currentUser);
        return message;
    }

    private void ensureConversationMember(Conversation conversation, User currentUser) {
        boolean isMember = conversation.getMembers().stream()
                .anyMatch(member -> member.getId().equals(currentUser.getId()));
        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }
    }

    private void ensurePollOpen(Message poll, Map<String, Object> metadata) {
        if (Boolean.TRUE.equals(metadata.get("locked"))) {
            throw new BadRequestException("Poll is locked");
        }
        if (isPollExpired(metadata)) {
            lockPollMetadata(poll, metadata);
            broadcastMessageUpdate(poll.getConversation(), mapToMessageResponse(poll));
            throw new BadRequestException("Poll has expired");
        }
    }

    private boolean lockExpiredPollIfNeeded(Message poll) {
        if (poll.getMessageType() != MessageType.POLL || poll.getMetadata() == null) {
            return false;
        }

        Map<String, Object> metadata = mutableMetadata(poll);
        if (Boolean.TRUE.equals(metadata.get("locked")) || !isPollExpired(metadata)) {
            return false;
        }

        lockPollMetadata(poll, metadata);
        return true;
    }

    private void lockPollMetadata(Message poll, Map<String, Object> metadata) {
        metadata.put("locked", true);
        metadata.put("lockedAt", Instant.now().toString());
        poll.setMetadata(metadata);
        compareAndSetPollMetadata(poll, metadata);
    }

    private boolean isPollExpired(Map<String, Object> metadata) {
        Object expiresAtValue = metadata.get("expiresAt");
        if (!(expiresAtValue instanceof String expiresAtString) || expiresAtString.isBlank()) {
            return false;
        }

        return parsePollExpiresAt(expiresAtString)
                .map(expiresAt -> !Instant.now().isBefore(expiresAt))
                .orElse(false);
    }

    private String normalizePollExpiresAt(String expiresAtString) {
        if (expiresAtString == null || expiresAtString.isBlank()) {
            return null;
        }

        return parsePollExpiresAt(expiresAtString.trim())
                .map(Instant::toString)
                .orElseThrow(() -> new BadRequestException("Invalid poll expiration time"));
    }

    private Optional<Instant> parsePollExpiresAt(String expiresAtString) {
        try {
            return Optional.of(Instant.parse(expiresAtString));
        } catch (DateTimeParseException ignored) {
            // Fall through for older values stored before poll expiry included a zone.
        }

        try {
            return Optional.of(OffsetDateTime.parse(expiresAtString).toInstant());
        } catch (DateTimeParseException ignored) {
            // Fall through for legacy LocalDateTime metadata.
        }

        try {
            return Optional.of(LocalDateTime.parse(expiresAtString).atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    private boolean canManagePoll(Message poll, User currentUser) {
        if (poll.getSender() != null && poll.getSender().getId().equals(currentUser.getId())) {
            return true;
        }
        return canModerateGroup(poll.getConversation(), currentUser);
    }

    private boolean canModerateGroup(Conversation conversation, User currentUser) {
        if (conversation.getType() != ConversationType.GROUP) {
            return true;
        }
        return getGroupRole(conversation, currentUser)
                .map(role -> isLeaderRole(role) || role == GroupRole.DEPUTY)
                .orElse(false);
    }

    private void ensureChannelPostingAllowed(Conversation conversation, User currentUser) {
        Channel channel = channelRepository.findByConversationId(conversation.getId()).orElse(null);
        if (channel == null || !channel.isPostingRestricted()) return;

        GroupRole role = getGroupRole(conversation, currentUser).orElse(null);
        if (role == null || (!isLeaderRole(role) && role != GroupRole.DEPUTY)) {
            throw new BadRequestException("Only group leaders and deputies can send messages in this channel");
        }
    }

    private boolean canModerateMessage(Conversation conversation, User actor, User messageSender) {
        if (conversation.getType() != ConversationType.GROUP) {
            return false;
        }
        Optional<GroupRole> actorRole = getGroupRole(conversation, actor);
        if (actorRole.isEmpty()) {
            return false;
        }
        if (isLeaderRole(actorRole.get())) {
            return true;
        }
        return actorRole.get() == GroupRole.DEPUTY;
    }

    private Optional<GroupRole> getGroupRole(Conversation conversation, User user) {
        Group group = channelRepository.findByConversationId(conversation.getId()).map(Channel::getGroup).orElse(null);
        if (group == null) return Optional.empty();
        return groupMemberRepository.findByGroupIdAndUserId(group.getId(), user.getId())
                .map(member -> member.getRole());
    }

    private boolean isLeaderRole(GroupRole role) {
        return role == GroupRole.OWNER || role == GroupRole.LEADER || role == GroupRole.ADMIN;
    }

    private Map<String, Object> mutableMetadata(Message message) {
        return new HashMap<>(message.getMetadata() != null ? message.getMetadata() : Map.of());
    }

    private Message compareAndSetPollMetadata(Message poll, Map<String, Object> metadata) {
        return compareAndSetMutation(poll, new Update().set("metadata", metadata));
    }

    private Message compareAndSetMutation(Message current, Update update) {
        Criteria versionCriteria = current.getMutationVersion() == 0L
                ? new Criteria().orOperator(
                        Criteria.where("mutationVersion").is(0L),
                        Criteria.where("mutationVersion").exists(false),
                        Criteria.where("mutationVersion").is(null))
                : Criteria.where("mutationVersion").is(current.getMutationVersion());
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(current.getId()),
                versionCriteria));
        update.inc("mutationVersion", 1L).currentDate("updatedAt");
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Message.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mutablePollOptions(Map<String, Object> metadata) {
        Object rawOptions = metadata.get("options");
        if (!(rawOptions instanceof List<?> rawList)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> options = new ArrayList<>();
        for (Object rawOption : rawList) {
            if (rawOption instanceof Map<?, ?> rawMap) {
                options.add(new HashMap<>((Map<String, Object>) rawMap));
            }
        }
        return options;
    }

    @SuppressWarnings("unchecked")
    private List<String> getVoterIds(Map<String, Object> option) {
        Object rawVoterIds = option.get("voterIds");
        if (rawVoterIds instanceof List<?> rawList) {
            return rawList.stream().map(String::valueOf).collect(Collectors.toCollection(ArrayList::new));
        }
        List<String> voterIds = new ArrayList<>();
        option.put("voterIds", voterIds);
        return voterIds;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getVoters(Map<String, Object> option) {
        Object rawVoters = option.get("voters");
        if (rawVoters instanceof List<?> rawList) {
            List<Map<String, Object>> voters = new ArrayList<>();
            for (Object rawVoter : rawList) {
                if (rawVoter instanceof Map<?, ?> rawMap) {
                    voters.add(new HashMap<>((Map<String, Object>) rawMap));
                }
            }
            return voters;
        }
        List<Map<String, Object>> voters = new ArrayList<>();
        option.put("voters", voters);
        return voters;
    }

    private void addVoter(Map<String, Object> option, User user) {
        List<String> voterIds = getVoterIds(option);
        if (!voterIds.contains(user.getId())) {
            voterIds.add(user.getId());
        }

        List<Map<String, Object>> voters = getVoters(option);
        voters.removeIf(voter -> user.getId().equals(voter.get("id")));
        Map<String, Object> voter = new HashMap<>();
        voter.put("id", user.getId());
        voter.put("username", user.getUsername());
        voter.put("avatarUrl", user.getAvatarUrl());
        voters.add(voter);

        option.put("voterIds", voterIds);
        option.put("voters", voters);
    }

    private void removeVoter(Map<String, Object> option, String userId) {
        List<String> voterIds = getVoterIds(option);
        voterIds.removeIf(id -> id.equals(userId));
        List<Map<String, Object>> voters = getVoters(option);
        voters.removeIf(voter -> userId.equals(voter.get("id")));
        option.put("voterIds", voterIds);
        option.put("voters", voters);
    }

    private String buildPinSystemContent(Message message, boolean pin) {
        if (!pin) {
            return "đã bỏ ghim tin nhắn.";
        }

        if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
            boolean hasImage = message.getAttachments().stream()
                    .anyMatch(attachment -> "IMAGE".equalsIgnoreCase(attachment.getType()));
            if (hasImage) {
                return "đã ghim 1 tin nhắn hình ảnh.";
            }
            return "đã ghim 1 tin nhắn tệp.";
        }

        if (message.getMessageType() == MessageType.IMAGE) {
            return "đã ghim 1 tin nhắn hình ảnh.";
        }
        if (message.getMessageType() == MessageType.FILE) {
            return "đã ghim 1 tin nhắn tệp.";
        }

        String preview = toPlainText(message.getContent());
        if (preview.length() > 40) {
            preview = preview.substring(0, 37) + "...";
        }
        return preview.isEmpty() ? "đã ghim tin nhắn." : "đã ghim tin nhắn " + preview;
    }

    private String toPlainText(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        return HTML_TAG_PATTERN.matcher(content)
                .replaceAll(" ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // @Transactional
    public MessageResponse editMessage(String messageId, EditMessageRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));

        if (!message.getSender().getId().equals(currentUser.getId())) {
            throw new BadRequestException("You can only edit your own messages");
        }
        if (message.isRecalled()) {
            throw new BadRequestException("Cannot edit a recalled message");
        }
        if (message.getMessageType() == MessageType.SYSTEM || message.getMessageType() == MessageType.POLL) {
            throw new BadRequestException("Cannot edit this message type");
        }

        message.setContent(request.getContent());
        message.setEdited(true);
        message.setEditedAt(LocalDateTime.now());
        Message savedMessage = messageRepository.save(message);

        MessageResponse response = mapToMessageResponse(savedMessage);
        broadcastMessageUpdate(message.getConversation(), response);
        return response;
    }

    // @Transactional
    public MessageResponse recallMessage(String messageId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));

        if (!message.getSender().getId().equals(currentUser.getId())
                && !canModerateMessage(message.getConversation(), currentUser, message.getSender())) {
            throw new BadRequestException("You can only recall your own messages");
        }
        if (message.getMessageType() == MessageType.SYSTEM || message.getMessageType() == MessageType.POLL) {
            throw new BadRequestException("Cannot recall this message type");
        }

        message.setRecalled(true);
        message.setContent("Tin nhắn đã bị thu hồi");
        Message savedMessage = messageRepository.save(message);

        MessageResponse response = mapToMessageResponse(savedMessage);
        broadcastMessageUpdate(message.getConversation(), response);
        return response;
    }

    public MessageResponse recallAttachment(String messageId, String attachmentUrl) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));

        if (!message.getSender().getId().equals(currentUser.getId())
                && !canModerateMessage(message.getConversation(), currentUser, message.getSender())) {
            throw new BadRequestException("You can only recall attachments from your own messages");
        }
        if (message.isRecalled()) {
            throw new BadRequestException("Cannot recall attachments from a recalled message");
        }

        List<MessageAttachment> attachments = message.getAttachments();
        if (attachments != null && !attachments.isEmpty() && attachmentUrl != null && !attachmentUrl.isBlank()) {
            attachments.removeIf(a -> attachmentUrl.trim().equalsIgnoreCase(a.getUrl().trim()));
            message.setAttachments(attachments);
        }

        String textContent = toPlainText(message.getContent());
        if ((message.getAttachments() == null || message.getAttachments().isEmpty()) && textContent.isBlank()) {
            message.setRecalled(true);
            message.setContent("Tin nhắn đã bị thu hồi");
        }

        Message savedMessage = messageRepository.save(message);
        MessageResponse response = mapToMessageResponse(savedMessage);
        broadcastMessageUpdate(message.getConversation(), response);
        return response;
    }

    // @Transactional
    public void deleteMessageForMe(String messageId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));

        boolean isMember = message.getConversation().getMembers().stream()
                .anyMatch(m -> m.getId().equals(currentUser.getId()));
        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }

        if (message.getDeletedByUsers() == null) {
            message.setDeletedByUsers(new ArrayList<>());
        }
        if (!message.getDeletedByUsers().contains(currentUser.getId())) {
            message.getDeletedByUsers().add(currentUser.getId());
            messageRepository.save(message);
        }
    }

    @Override
    public MessageResponse getMessageForCurrentUser(String messageId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        ensureConversationMember(message.getConversation(), currentUser);
        if (message.getDeletedByUsers() != null && message.getDeletedByUsers().contains(currentUser.getId())) {
            throw new ResourceNotFoundException("Message not found");
        }
        return mapToMessageResponse(message);
    }

    // @Transactional
    public MessageResponse pinMessage(String messageId, boolean pin) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));

        boolean isMember = message.getConversation().getMembers().stream()
                .anyMatch(m -> m.getId().equals(currentUser.getId()));
        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }
        if (message.isRecalled()) {
            throw new BadRequestException("Cannot pin a recalled message");
        }
        if (message.getMessageType() == MessageType.SYSTEM) {
            throw new BadRequestException("Cannot pin this message type");
        }
        if (!canModerateGroup(message.getConversation(), currentUser)) {
            throw new BadRequestException("Only the group leader or deputy can pin messages");
        }

        message.setPinned(pin);
        message.setPinnedAt(pin ? LocalDateTime.now() : null);
        Message savedMessage = messageRepository.save(message);

        MessageResponse response = mapToMessageResponse(savedMessage);
        broadcastMessageUpdate(message.getConversation(), response);
        createAndBroadcastSystemMessage(message.getConversation(), currentUser, buildPinSystemContent(savedMessage, pin));
        return response;
    }

    // @Transactional(readOnly = true)
    public List<MessageResponse> getPinnedMessages(String conversationId) {
        return getPinnedMessages(conversationId, null, 50).getItems();
    }

    @Override
    public MessageCursorPageResponse getPinnedMessages(String conversationId, String encodedCursor, int limit) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        ensureConversationMember(conversationId, currentUser);
        int safeLimit = Math.min(50, Math.max(1, limit));
        MessageCursorCodec.Cursor cursor = MessageCursorCodec.decode(encodedCursor);
        List<Criteria> filters = new ArrayList<>();
        filters.add(visibleConversationCriteria(conversationId, currentUser.getId(), LocalDateTime.now()));
        filters.add(Criteria.where("isPinned").is(true));
        if (cursor != null) filters.add(cursorBoundary(cursor, true));

        Query query = new Query(new Criteria().andOperator(filters.toArray(Criteria[]::new)))
                .with(stableMessageSort(Sort.Direction.DESC))
                .limit(safeLimit + 1);
        List<Message> messages = new ArrayList<>(mongoTemplate.find(query, Message.class));
        boolean hasMore = messages.size() > safeLimit;
        if (hasMore) messages.remove(messages.size() - 1);
        Message last = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        return MessageCursorPageResponse.builder()
                .items(mapMessagesToResponses(messages))
                .nextCursor(hasMore && last != null
                        ? MessageCursorCodec.encode(last.getCreatedAt(), last.getId())
                        : null)
                .hasMore(hasMore)
                .build();
    }

    // @Transactional
    public MessageResponse reactToMessage(String messageId, ReactMessageRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        for (int attempt = 0; attempt < MAX_ATOMIC_MUTATION_ATTEMPTS; attempt++) {
            Message message = messageRepository.findById(messageId)
                    .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));

            ensureConversationMember(message.getConversation(), currentUser);
            if (message.isRecalled()) {
                throw new BadRequestException("Cannot react to a recalled message");
            }
            if (message.getExpiresAt() != null && !message.getExpiresAt().isAfter(LocalDateTime.now())) {
                throw new BadRequestException("Cannot react to an expired message");
            }
            if (message.getMessageType() == MessageType.SYSTEM || message.getMessageType() == MessageType.POLL) {
                throw new BadRequestException("Cannot react to this message type");
            }

            List<MessageReaction> reactions = message.getReactions() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(message.getReactions());
            boolean removeExisting = reactions.stream().anyMatch(reaction ->
                    currentUser.getId().equals(reaction.getUserId())
                            && request.getEmoji().equals(reaction.getEmoji()));
            reactions.removeIf(reaction -> currentUser.getId().equals(reaction.getUserId()));
            if (!removeExisting) {
                reactions.add(MessageReaction.builder()
                        .userId(currentUser.getId())
                        .username(currentUser.getUsername())
                        .emoji(request.getEmoji())
                        .build());
            }

            Message savedMessage = compareAndSetMutation(message, new Update().set("reactions", reactions));
            if (savedMessage == null) {
                continue;
            }

            boolean reactionAdded = savedMessage.getReactions() != null
                    && savedMessage.getReactions().stream().anyMatch(reaction ->
                    currentUser.getId().equals(reaction.getUserId())
                            && request.getEmoji().equals(reaction.getEmoji()));
            MessageResponse response = mapToMessageResponse(savedMessage);
            Map<String, Object> realtimeMetadata = new HashMap<>(
                    response.getMetadata() == null ? Map.of() : response.getMetadata()
            );
            realtimeMetadata.put("realtimeEvent", "REACTION_UPDATED");
            realtimeMetadata.put("reactionAdded", reactionAdded);
            realtimeMetadata.put("reactionUserId", currentUser.getId());
            realtimeMetadata.put("reactionUsername", currentUser.getUsername());
            realtimeMetadata.put("reactionEmoji", request.getEmoji());
            response.setMetadata(realtimeMetadata);
            broadcastMessageUpdate(savedMessage.getConversation(), response);
            return response;
        }
        throw new ConflictException("Message reactions changed concurrently; please retry");
    }

    // @Transactional
    public List<MessageResponse> shareMessage(String messageId, ShareMessageRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Message sourceMessage = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));

        Conversation sourceConversation = sourceMessage.getConversation();
        boolean canReadSource = sourceConversation.getMembers().stream()
                .anyMatch(m -> m.getId().equals(currentUser.getId()));
        if (!canReadSource) {
            throw new BadRequestException("You are not a member of the source conversation");
        }
        if (sourceMessage.isRecalled()) {
            throw new BadRequestException("Cannot share a recalled message");
        }
        if (sourceMessage.getMessageType() == MessageType.SYSTEM || sourceMessage.getMessageType() == MessageType.POLL) {
            throw new BadRequestException("Cannot share this message type");
        }
        if (sourceMessage.getDeletedByUsers() != null && sourceMessage.getDeletedByUsers().contains(currentUser.getId())) {
            throw new BadRequestException("Cannot share a message that was deleted for you");
        }

        List<String> targetConversationIds = request.getTargetConversationIds().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .distinct()
                .toList();

        if (targetConversationIds.isEmpty()) {
            throw new BadRequestException("Target conversation IDs are required");
        }

        List<MessageResponse> sharedMessages = new ArrayList<>();
        String forwardedContent = combineForwardContent(request.getAccompanyingText(), sourceMessage.getContent());
        for (String targetConversationId : targetConversationIds) {
            MessageRequest messageRequest = MessageRequest.builder()
                    .conversationId(targetConversationId)
                    .content(forwardedContent)
                    .messageType(sourceMessage.getMessageType().name())
                    .attachments(sourceMessage.getAttachments())
                    .build();

            sharedMessages.add(sendMessageWithUser(
                    messageRequest,
                    currentUser,
                    sourceMessage.getId(),
                    sourceMessage.getSender().getUsername()
            ));
        }

        return sharedMessages;
    }

    static String combineForwardContent(String accompanyingText, String sourceContent) {
        String note = accompanyingText == null ? "" : accompanyingText.trim();
        String original = sourceContent == null ? "" : sourceContent.trim();
        if (note.isEmpty()) return original;
        if (original.isEmpty()) return note;
        return note + "\n\n" + original;
    }

    // @Transactional(readOnly = true)
    public List<MessageResponse> searchMessages(String query, String conversationId) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) return Collections.emptyList();
        return searchMessagesCursor(
                normalizedQuery, conversationId, null, MessageType.TEXT,
                null, null, null, 50).getItems();
    }

    @Override
    public MessageSearchResponse searchMessagesAdvanced(
            String query,
            String conversationId,
            String senderId,
            MessageType messageType,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size
    ) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        List<String> permittedConversationIds = resolveSearchConversationIds(conversationId, currentUser);

        if (permittedConversationIds.isEmpty()) {
            return MessageSearchResponse.builder()
                    .items(List.of())
                    .page(safePage)
                    .size(safeSize)
                    .totalElements(0)
                    .hasMore(false)
                    .build();
        }
        Query countQuery = buildSearchQuery(
                query, permittedConversationIds, currentUser.getId(), senderId, messageType, from, to, null);
        long total = mongoTemplate.count(countQuery, Message.class);
        Query resultQuery = buildSearchQuery(
                query, permittedConversationIds, currentUser.getId(), senderId, messageType, from, to, null)
                .with(PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<MessageResponse> items = mapMessagesToResponses(mongoTemplate.find(resultQuery, Message.class));

        return MessageSearchResponse.builder()
                .items(items)
                .page(safePage)
                .size(safeSize)
                .totalElements(total)
                .hasMore((long) (safePage + 1) * safeSize < total)
                .build();
    }

    @Override
    public MessageCursorPageResponse searchMessagesCursor(
            String query,
            String conversationId,
            String senderId,
            MessageType messageType,
            LocalDateTime from,
            LocalDateTime to,
            String encodedCursor,
            int limit
    ) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        List<String> permittedConversationIds = resolveSearchConversationIds(conversationId, currentUser);
        if (permittedConversationIds.isEmpty()) {
            return MessageCursorPageResponse.builder()
                    .items(List.of())
                    .hasMore(false)
                    .build();
        }

        int safeLimit = Math.min(50, Math.max(1, limit));
        MessageCursorCodec.Cursor cursor = MessageCursorCodec.decode(encodedCursor);
        Query resultQuery = buildSearchQuery(
                query, permittedConversationIds, currentUser.getId(), senderId, messageType, from, to, cursor)
                .with(stableMessageSort(Sort.Direction.DESC))
                .limit(safeLimit + 1);
        List<Message> messages = new ArrayList<>(mongoTemplate.find(resultQuery, Message.class));
        boolean hasMore = messages.size() > safeLimit;
        if (hasMore) messages.remove(messages.size() - 1);
        Message last = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        return MessageCursorPageResponse.builder()
                .items(mapMessagesToResponses(messages))
                .nextCursor(hasMore && last != null
                        ? MessageCursorCodec.encode(last.getCreatedAt(), last.getId())
                        : null)
                .hasMore(hasMore)
                .build();
    }

    private List<String> resolveSearchConversationIds(String conversationId, User currentUser) {
        if (conversationId != null && !conversationId.isBlank()) {
            ensureConversationMember(conversationId, currentUser);
            return List.of(conversationId);
        }
        return conversationRepository.findAllByMembersIdOrderByUpdatedAtDesc(currentUser.getId())
                .stream()
                .map(Conversation::getId)
                .toList();
    }

    private Query buildSearchQuery(
            String query,
            List<String> permittedConversationIds,
            String currentUserId,
            String senderId,
            MessageType messageType,
            LocalDateTime from,
            LocalDateTime to,
            MessageCursorCodec.Cursor cursor
    ) {
        List<Criteria> filters = new ArrayList<>();
        filters.add(new Criteria().orOperator(
                Criteria.where("conversationId").in(permittedConversationIds),
                Criteria.where("conversation").in(permittedConversationIds)));
        filters.add(Criteria.where("deletedByUsers").ne(currentUserId));
        filters.add(Criteria.where("isRecalled").ne(true));
        filters.add(new Criteria().orOperator(
                Criteria.where("expiresAt").exists(false),
                Criteria.where("expiresAt").is(null),
                Criteria.where("expiresAt").gt(LocalDateTime.now())));
        if (senderId != null && !senderId.isBlank()) filters.add(Criteria.where("senderId").is(senderId));
        if (messageType != null) filters.add(Criteria.where("messageType").is(messageType));
        if (from != null || to != null) {
            Criteria createdAt = Criteria.where("createdAt");
            if (from != null) createdAt = createdAt.gte(from);
            if (to != null) createdAt = createdAt.lte(to);
            filters.add(createdAt);
        }
        if (cursor != null) filters.add(cursorBoundary(cursor, true));

        Query result = new Query(new Criteria().andOperator(filters.toArray(Criteria[]::new)));
        String normalizedQuery = query == null ? "" : query.trim();
        if (!normalizedQuery.isEmpty()) {
            result.addCriteria(TextCriteria.forDefaultLanguage().matching(normalizedQuery));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessagesForMe(List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return;
        for (String id : messageIds) {
            deleteMessageForMe(id);
        }
    }

    @Override
    public List<MessageResponse> recallMessages(List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return Collections.emptyList();
        List<MessageResponse> responses = new ArrayList<>();
        for (String id : messageIds) {
            responses.add(recallMessage(id));
        }
        return responses;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MessageResponse> shareMessages(iuh.fit.se.nextalk_be.dto.request.BatchShareMessageRequest request) {
        if (request.getMessageIds() == null || request.getMessageIds().isEmpty()) return Collections.emptyList();
        List<MessageResponse> responses = new ArrayList<>();
        for (String id : request.getMessageIds()) {
            iuh.fit.se.nextalk_be.dto.request.ShareMessageRequest singleShareReq = new iuh.fit.se.nextalk_be.dto.request.ShareMessageRequest();
            singleShareReq.setTargetConversationIds(request.getTargetConversationIds());
            responses.addAll(shareMessage(id, singleShareReq));
        }
        return responses;
    }
}
