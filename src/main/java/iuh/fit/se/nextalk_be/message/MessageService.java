package iuh.fit.se.nextalk_be.message;

import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.block.UserBlockRepository;
import iuh.fit.se.nextalk_be.chatrequest.ChatRequestRepository;
import iuh.fit.se.nextalk_be.chatrequest.ChatRequestStatus;
import iuh.fit.se.nextalk_be.conversation.Conversation;
import iuh.fit.se.nextalk_be.conversation.ConversationRepository;
import iuh.fit.se.nextalk_be.conversation.ConversationType;
import iuh.fit.se.nextalk_be.friend.FriendshipRepository;
import iuh.fit.se.nextalk_be.friend.FriendshipStatus;
import iuh.fit.se.nextalk_be.group.Group;
import iuh.fit.se.nextalk_be.group.GroupMemberRepository;
import iuh.fit.se.nextalk_be.group.GroupRepository;
import iuh.fit.se.nextalk_be.group.GroupRole;
import iuh.fit.se.nextalk_be.message.dto.*;
import iuh.fit.se.nextalk_be.user.User;
import iuh.fit.se.nextalk_be.user.UserRepository;
import iuh.fit.se.nextalk_be.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import iuh.fit.se.nextalk_be.notification.NotificationService;
import iuh.fit.se.nextalk_be.notification.NotificationType;
import org.springframework.data.domain.PageImpl;
import java.util.stream.Collectors;
import java.util.function.Function;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageStatusRepository messageStatusRepository;
    private final NotificationService notificationService;
    private final FriendshipRepository friendshipRepository;
    private final ChatRequestRepository chatRequestRepository;
    private final UserBlockRepository userBlockRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;

    // @Transactional
    public MessageResponse sendMessage(MessageRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        return sendMessageWithUser(request, currentUser);
    }

    // @Transactional
    public MessageResponse sendMessage(MessageRequest request, String senderEmail) {
        User currentUser = userRepository.findByEmail(senderEmail)
                .or(() -> userRepository.findByUsername(senderEmail))
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
        return sendMessageWithUser(request, currentUser);
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
        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with ID: " + request.getConversationId()));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(m -> m.getId().equals(currentUser.getId()));

        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }

        ensurePrivateMessageAllowed(conversation, currentUser);

        List<MessageAttachment> attachments = request.getAttachments() != null
                ? request.getAttachments().stream()
                .filter(attachment -> attachment != null && attachment.getUrl() != null && !attachment.getUrl().trim().isEmpty())
                .map(attachment -> MessageAttachment.builder()
                        .url(attachment.getUrl().trim())
                        .type(attachment.getType() != null ? attachment.getType().toUpperCase() : "FILE")
                        .name(attachment.getName())
                        .build())
                .toList()
                : List.of();

        String content = request.getContent() != null ? request.getContent().trim() : "";
        if (content.isEmpty() && attachments.isEmpty()) {
            throw new BadRequestException("Message content or attachments are required");
        }

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

        Message message = Message.builder()
                .conversation(conversation)
                .sender(currentUser)
                .content(content)
                .messageType(type)
                .attachments(attachments)
                .parentId(parentId)
                .forwardedFromMessageId(forwardedFromMessageId)
                .forwardedFromSenderUsername(forwardedFromSenderUsername)
                .build();

        Message savedMessage = messageRepository.save(message);

        // Create initial SENT status for all other conversation members
        List<MessageStatus> initialStatuses = new ArrayList<>();
        for (User member : conversation.getMembers()) {
            if (!member.getId().equals(currentUser.getId())) {
                MessageStatus statusRecord = MessageStatus.builder()
                        .message(savedMessage)
                        .user(member)
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

        // Broadcast to all conversation members over WebSocket and trigger notifications
        for (User member : conversation.getMembers()) {
            // Send to user-specific private queue: /user/{username}/queue/private
            messagingTemplate.convertAndSendToUser(
                    member.getUsername(),
                    "/queue/private",
                    response
            );

            // Create notification for other members
            if (!member.getId().equals(currentUser.getId())) {
                String contentPreview;
                if (savedMessage.getMessageType() == MessageType.IMAGE) {
                    contentPreview = "[Hình ảnh]";
                } else if (savedMessage.getMessageType() == MessageType.VIDEO) {
                    contentPreview = "[Video]";
                } else if (savedMessage.getMessageType() == MessageType.FILE) {
                    contentPreview = "[Tệp đính kèm]";
                } else {
                    contentPreview = savedMessage.getContent();
                }

                String notificationContent = "Bạn có tin nhắn mới từ " + currentUser.getUsername() + ": " + 
                        (contentPreview.length() > 60 ? contentPreview.substring(0, 57) + "..." : contentPreview);

                notificationService.createAndSend(
                        member,
                        NotificationType.NEW_MESSAGE,
                        notificationContent,
                        conversation.getId().toString()
                );
            }
        }

        return response;
    }

    private void ensurePrivateMessageAllowed(Conversation conversation, User currentUser) {
        if (conversation.getType() != ConversationType.PRIVATE) {
            return;
        }

        User otherMember = conversation.getMembers().stream()
                .filter(member -> !member.getId().equals(currentUser.getId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Private conversation must have another member"));

        if (userBlockRepository.existsBetweenUsers(currentUser.getId(), otherMember.getId())) {
            throw new BadRequestException("You cannot message this user because one of you has blocked the other.");
        }

        boolean areFriends = friendshipRepository.findRelation(currentUser.getId(), otherMember.getId())
                .map(friendship -> friendship.getStatus() == FriendshipStatus.ACCEPTED)
                .orElse(false);

        boolean hasAcceptedChatRequest = chatRequestRepository
                .findBySenderIdAndReceiverIdAndStatus(currentUser.getId(), otherMember.getId(), ChatRequestStatus.ACCEPTED)
                .isPresent()
                || chatRequestRepository
                .findBySenderIdAndReceiverIdAndStatus(otherMember.getId(), currentUser.getId(), ChatRequestStatus.ACCEPTED)
                .isPresent();

        if (!areFriends && !hasAcceptedChatRequest) {
            throw new BadRequestException("You are no longer friends. Send a chat request to continue messaging.");
        }
    }

    // @Transactional(readOnly = true)
    public Page<MessageResponse> getConversationMessages(String conversationId, Pageable pageable) {
        User currentUser = userService.getCurrentAuthenticatedUser();

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with ID: " + conversationId));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(m -> m.getId().equals(currentUser.getId()));

        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }

        Page<Message> messages = messageRepository.findByConversationIdAndDeletedByUsersNotContainingOrderByCreatedAtDesc(
                conversationId, currentUser.getId(), pageable
        );
        List<MessageResponse> content = mapMessagesToResponses(messages.getContent());
        return new PageImpl<>(content, pageable, messages.getTotalElements());
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

        List<Message> messages = messageRepository.findAllByConversationId(conversationId);
        if (messages.isEmpty()) {
            return;
        }

        List<String> messageIds = messages.stream().map(Message::getId).toList();

        List<MessageStatus> statusesToUpdate = messageStatusRepository.findAllByUserIdAndMessageIdInAndStatusIn(
                user.getId(), messageIds, List.of("SENT")
        );

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

        List<Message> messages = messageRepository.findAllByConversationId(conversationId);
        if (messages.isEmpty()) {
            return;
        }

        List<String> messageIds = messages.stream().map(Message::getId).toList();

        List<MessageStatus> statusesToUpdate = messageStatusRepository.findAllByUserIdAndMessageIdInAndStatusIn(
                user.getId(), messageIds, List.of("SENT", "DELIVERED")
        );

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

    private MessageResponse mapToMessageResponse(Message message) {
        List<MessageStatus> statusRecords = messageStatusRepository.findAllByMessageId(message.getId());
        return mapToMessageResponseWithStatuses(message, statusRecords);
    }

    private MessageResponse mapToMessageResponseWithStatuses(Message message, List<MessageStatus> statusRecords) {
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
                .reactions(message.getReactions() != null ? message.getReactions() : new ArrayList<>())
                .metadata(message.getMetadata() != null ? message.getMetadata() : Map.of())
                .build();
    }

    private MessageResponse mapToMessageResponseOptimized(
            Message message,
            List<MessageStatus> statusRecords,
            Map<String, String> usernameMap
    ) {
        List<MessageStatusResponse> statusResponses = statusRecords.stream()
                .map(status -> {
                    String userId = status.getUser().getId();
                    String username = usernameMap.getOrDefault(userId, "unknown");
                    return MessageStatusResponse.builder()
                            .userId(userId)
                            .username(username)
                            .status(status.getStatus())
                            .updatedAt(status.getUpdatedAt() != null ? status.getUpdatedAt() : status.getCreatedAt() != null ? status.getCreatedAt() : LocalDateTime.now())
                            .build();
                })
                .toList();

        String senderId = message.getSender().getId();
        String senderUsername = usernameMap.getOrDefault(senderId, "unknown");

        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderId(senderId)
                .senderUsername(senderUsername)
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
                .reactions(message.getReactions() != null ? message.getReactions() : new ArrayList<>())
                .metadata(message.getMetadata() != null ? message.getMetadata() : Map.of())
                .build();
    }

    private List<MessageResponse> mapMessagesToResponses(List<Message> messageList) {
        if (messageList == null || messageList.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> messageIds = messageList.stream().map(Message::getId).toList();
        List<MessageStatus> allStatuses = messageStatusRepository.findAllByMessageIdIn(messageIds);

        // Collect all user IDs to batch fetch usernames
        Set<String> userIds = new HashSet<>();
        for (Message msg : messageList) {
            if (msg.getSender() != null) {
                userIds.add(msg.getSender().getId());
            }
        }
        for (MessageStatus status : allStatuses) {
            if (status.getUser() != null) {
                userIds.add(status.getUser().getId());
            }
        }

        List<User> users = userRepository.findAllById(userIds);
        Map<String, String> usernameMap = new HashMap<>();
        for (User u : users) {
            usernameMap.put(u.getId(), u.getUsername());
        }

        // Group statuses by Message ID
        Map<String, List<MessageStatus>> statusMap = new HashMap<>();
        for (MessageStatus status : allStatuses) {
            if (status.getMessage() != null) {
                statusMap.computeIfAbsent(status.getMessage().getId(), k -> new ArrayList<>()).add(status);
            }
        }

        List<MessageResponse> responses = new ArrayList<>();
        for (Message message : messageList) {
            responses.add(mapToMessageResponseOptimized(
                    message,
                    statusMap.getOrDefault(message.getId(), List.of()),
                    usernameMap
            ));
        }
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

    public MessageResponse createPoll(CreatePollRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with ID: " + request.getConversationId()));

        if (conversation.getType() != ConversationType.GROUP) {
            throw new BadRequestException("Polls can only be created in group conversations");
        }
        ensureConversationMember(conversation, currentUser);

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
        metadata.put("expiresAt", request.getExpiresAt() != null ? request.getExpiresAt().toString() : null);
        metadata.put("creatorId", currentUser.getId());
        metadata.put("creatorName", currentUser.getUsername());
        metadata.put("options", options);

        Message poll = Message.builder()
                .conversation(conversation)
                .sender(currentUser)
                .content(request.getQuestion().trim())
                .messageType(MessageType.POLL)
                .metadata(metadata)
                .isPinned(true)
                .pinnedAt(LocalDateTime.now())
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
        Message poll = getPollMessageForMember(messageId, currentUser);
        Map<String, Object> metadata = mutableMetadata(poll);
        ensurePollOpen(metadata);

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
        poll.setMetadata(metadata);
        Message savedPoll = messageRepository.save(poll);
        MessageResponse response = mapToMessageResponse(savedPoll);
        broadcastMessageUpdate(poll.getConversation(), response);
        return response;
    }

    public MessageResponse addPollOption(String messageId, AddPollOptionRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Message poll = getPollMessageForMember(messageId, currentUser);
        Map<String, Object> metadata = mutableMetadata(poll);
        ensurePollOpen(metadata);

        if (!Boolean.TRUE.equals(metadata.get("allowAddOptions")) && !canManagePoll(poll, currentUser)) {
            throw new BadRequestException("This poll does not allow members to add options");
        }

        String text = request.getText() != null ? request.getText().trim() : "";
        if (text.isEmpty()) {
            throw new BadRequestException("Option text is required");
        }

        List<Map<String, Object>> options = mutablePollOptions(metadata);
        boolean exists = options.stream().anyMatch(option -> text.equalsIgnoreCase(String.valueOf(option.get("text"))));
        if (exists) {
            throw new BadRequestException("Poll option already exists");
        }

        options.add(createPollOptionMetadata(text, currentUser));
        metadata.put("options", options);
        poll.setMetadata(metadata);
        Message savedPoll = messageRepository.save(poll);
        MessageResponse response = mapToMessageResponse(savedPoll);
        broadcastMessageUpdate(poll.getConversation(), response);
        return response;
    }

    public MessageResponse lockPoll(String messageId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Message poll = getPollMessageForMember(messageId, currentUser);
        if (!canManagePoll(poll, currentUser)) {
            throw new BadRequestException("Only the poll creator or group admins can lock this poll");
        }

        Map<String, Object> metadata = mutableMetadata(poll);
        metadata.put("locked", true);
        metadata.put("lockedAt", LocalDateTime.now().toString());
        poll.setMetadata(metadata);
        Message savedPoll = messageRepository.save(poll);
        MessageResponse response = mapToMessageResponse(savedPoll);
        broadcastMessageUpdate(poll.getConversation(), response);
        return response;
    }

    public MessageResponse deletePoll(String messageId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Message poll = getPollMessageForMember(messageId, currentUser);
        if (!canManagePoll(poll, currentUser)) {
            throw new BadRequestException("Only the poll creator or group admins can delete this poll");
        }

        poll.setRecalled(true);
        poll.setPinned(false);
        poll.setPinnedAt(null);
        poll.setContent("Binh chon da bi xoa");
        Message savedPoll = messageRepository.save(poll);
        MessageResponse response = mapToMessageResponse(savedPoll);
        broadcastMessageUpdate(poll.getConversation(), response);
        return response;
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

    private void ensurePollOpen(Map<String, Object> metadata) {
        if (Boolean.TRUE.equals(metadata.get("locked"))) {
            throw new BadRequestException("Poll is locked");
        }
        Object expiresAtValue = metadata.get("expiresAt");
        if (expiresAtValue instanceof String expiresAtString && !expiresAtString.isBlank()) {
            LocalDateTime expiresAt = LocalDateTime.parse(expiresAtString);
            if (LocalDateTime.now().isAfter(expiresAt)) {
                metadata.put("locked", true);
                throw new BadRequestException("Poll has expired");
            }
        }
    }

    private boolean canManagePoll(Message poll, User currentUser) {
        if (poll.getSender() != null && poll.getSender().getId().equals(currentUser.getId())) {
            return true;
        }
        Group group = groupRepository.findByConversationId(poll.getConversation().getId()).orElse(null);
        if (group == null) return false;
        return groupMemberRepository.findByGroupIdAndUserId(group.getId(), currentUser.getId())
                .map(member -> member.getRole() == GroupRole.OWNER || member.getRole() == GroupRole.ADMIN)
                .orElse(false);
    }

    private Map<String, Object> mutableMetadata(Message message) {
        return new HashMap<>(message.getMetadata() != null ? message.getMetadata() : Map.of());
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

        String preview = message.getContent() != null ? message.getContent().trim() : "";
        if (preview.length() > 40) {
            preview = preview.substring(0, 37) + "...";
        }
        return preview.isEmpty() ? "đã ghim tin nhắn." : "đã ghim tin nhắn " + preview;
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

        if (!message.getSender().getId().equals(currentUser.getId())) {
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
        if (message.getMessageType() == MessageType.SYSTEM || message.getMessageType() == MessageType.POLL) {
            throw new BadRequestException("Cannot pin this message type");
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
        User currentUser = userService.getCurrentAuthenticatedUser();
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with ID: " + conversationId));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(m -> m.getId().equals(currentUser.getId()));
        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }

        List<Message> pinnedMessages = messageRepository.findByConversationIdAndIsPinnedTrue(conversationId);
        List<Message> filtered = pinnedMessages.stream()
                .filter(m -> m.getDeletedByUsers() == null || !m.getDeletedByUsers().contains(currentUser.getId()))
                .toList();
        return mapMessagesToResponses(filtered);
    }

    // @Transactional
    public MessageResponse reactToMessage(String messageId, ReactMessageRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));

        boolean isMember = message.getConversation().getMembers().stream()
                .anyMatch(m -> m.getId().equals(currentUser.getId()));
        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }
        if (message.isRecalled()) {
            throw new BadRequestException("Cannot react to a recalled message");
        }
        if (message.getMessageType() == MessageType.SYSTEM || message.getMessageType() == MessageType.POLL) {
            throw new BadRequestException("Cannot react to this message type");
        }

        if (message.getReactions() == null) {
            message.setReactions(new ArrayList<>());
        }

        Optional<MessageReaction> existing = message.getReactions().stream()
                .filter(r -> r.getUserId().equals(currentUser.getId()) && r.getEmoji().equals(request.getEmoji()))
                .findFirst();

        if (existing.isPresent()) {
            message.getReactions().remove(existing.get());
        } else {
            message.getReactions().removeIf(r -> r.getUserId().equals(currentUser.getId()));
            message.getReactions().add(MessageReaction.builder()
                    .userId(currentUser.getId())
                    .username(currentUser.getUsername())
                    .emoji(request.getEmoji())
                    .build());
        }

        Message savedMessage = messageRepository.save(message);

        MessageResponse response = mapToMessageResponse(savedMessage);
        broadcastMessageUpdate(message.getConversation(), response);
        return response;
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
        for (String targetConversationId : targetConversationIds) {
            MessageRequest messageRequest = MessageRequest.builder()
                    .conversationId(targetConversationId)
                    .content(sourceMessage.getContent())
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

    // @Transactional(readOnly = true)
    public List<MessageResponse> searchMessages(String query, String conversationId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        String trimmedQuery = query.trim();
        if (trimmedQuery.isEmpty()) {
            return Collections.emptyList();
        }

        List<Message> messages;
        if (conversationId != null) {
            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
            boolean isMember = conversation.getMembers().stream()
                    .anyMatch(m -> m.getId().equals(currentUser.getId()));
            if (!isMember) {
                throw new BadRequestException("You are not a member of this conversation");
            }
            messages = messageRepository.findByConversationIdAndContentContainingIgnoreCaseAndMessageTypeAndIsRecalledFalse(
                    conversationId, trimmedQuery, MessageType.TEXT
            );
        } else {
            List<Conversation> userConversations = conversationRepository.findAllByMembersIdOrderByUpdatedAtDesc(currentUser.getId());
            List<String> conversationIds = userConversations.stream().map(Conversation::getId).toList();
            if (conversationIds.isEmpty()) {
                return Collections.emptyList();
            }
            messages = messageRepository.findByConversationIdInAndContentContainingIgnoreCaseAndMessageTypeAndIsRecalledFalse(
                    conversationIds, trimmedQuery, MessageType.TEXT
            );
        }

        List<Message> filtered = messages.stream()
                .filter(m -> m.getDeletedByUsers() == null || !m.getDeletedByUsers().contains(currentUser.getId()))
                .toList();
        return mapMessagesToResponses(filtered);
    }
}
