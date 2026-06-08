package iuh.fit.se.nextalk_be.message;

import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.conversation.Conversation;
import iuh.fit.se.nextalk_be.conversation.ConversationRepository;
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

    @Transactional
    public MessageResponse sendMessage(MessageRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        return sendMessageWithUser(request, currentUser);
    }

    @Transactional
    public MessageResponse sendMessage(MessageRequest request, String senderEmail) {
        User currentUser = userRepository.findByEmail(senderEmail)
                .or(() -> userRepository.findByUsername(senderEmail))
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
        return sendMessageWithUser(request, currentUser);
    }

    private MessageResponse sendMessageWithUser(MessageRequest request, User currentUser) {
        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with ID: " + request.getConversationId()));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(m -> m.getId().equals(currentUser.getId()));

        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }

        MessageType type = MessageType.TEXT;
        if (request.getMessageType() != null) {
            try {
                type = MessageType.valueOf(request.getMessageType().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid message type: " + request.getMessageType());
            }
        }

        UUID parentId = request.getParentId();
        if (parentId != null) {
            Message parent = messageRepository.findById(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent message not found with ID: " + parentId));
            if (!parent.getConversation().getId().equals(conversation.getId())) {
                throw new BadRequestException("Parent message must be in the same conversation");
            }
        }

        Message message = Message.builder()
                .conversation(conversation)
                .sender(currentUser)
                .content(request.getContent())
                .messageType(type)
                .parentId(parentId)
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

    @Transactional(readOnly = true)
    public Page<MessageResponse> getConversationMessages(UUID conversationId, Pageable pageable) {
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

    @Transactional
    public void markConversationMessagesAsDelivered(UUID conversationId, String username) {
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

        List<UUID> messageIds = messages.stream().map(Message::getId).toList();

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

    @Transactional
    public void markConversationMessagesAsSeen(UUID conversationId, String username) {
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

        List<UUID> messageIds = messages.stream().map(Message::getId).toList();

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
                .createdAt(message.getCreatedAt() != null ? message.getCreatedAt() : LocalDateTime.now())
                .statuses(statusResponses)
                .parentId(message.getParentId())
                .isEdited(message.isEdited())
                .editedAt(message.getEditedAt())
                .isRecalled(message.isRecalled())
                .isPinned(message.isPinned())
                .reactions(message.getReactions() != null ? message.getReactions() : new ArrayList<>())
                .build();
    }

    private MessageResponse mapToMessageResponseOptimized(
            Message message,
            List<MessageStatus> statusRecords,
            Map<UUID, String> usernameMap
    ) {
        List<MessageStatusResponse> statusResponses = statusRecords.stream()
                .map(status -> {
                    UUID userId = status.getUser().getId();
                    String username = usernameMap.getOrDefault(userId, "unknown");
                    return MessageStatusResponse.builder()
                            .userId(userId)
                            .username(username)
                            .status(status.getStatus())
                            .updatedAt(status.getUpdatedAt() != null ? status.getUpdatedAt() : status.getCreatedAt() != null ? status.getCreatedAt() : LocalDateTime.now())
                            .build();
                })
                .toList();

        UUID senderId = message.getSender().getId();
        String senderUsername = usernameMap.getOrDefault(senderId, "unknown");

        return MessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .senderId(senderId)
                .senderUsername(senderUsername)
                .content(message.getContent())
                .messageType(message.getMessageType().name())
                .createdAt(message.getCreatedAt() != null ? message.getCreatedAt() : LocalDateTime.now())
                .statuses(statusResponses)
                .parentId(message.getParentId())
                .isEdited(message.isEdited())
                .editedAt(message.getEditedAt())
                .isRecalled(message.isRecalled())
                .isPinned(message.isPinned())
                .reactions(message.getReactions() != null ? message.getReactions() : new ArrayList<>())
                .build();
    }

    private List<MessageResponse> mapMessagesToResponses(List<Message> messageList) {
        if (messageList == null || messageList.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> messageIds = messageList.stream().map(Message::getId).toList();
        List<MessageStatus> allStatuses = messageStatusRepository.findAllByMessageIdIn(messageIds);

        // Collect all user IDs to batch fetch usernames
        Set<UUID> userIds = new HashSet<>();
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
        Map<UUID, String> usernameMap = new HashMap<>();
        for (User u : users) {
            usernameMap.put(u.getId(), u.getUsername());
        }

        // Group statuses by Message ID
        Map<UUID, List<MessageStatus>> statusMap = new HashMap<>();
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

    @Transactional
    public MessageResponse editMessage(UUID messageId, EditMessageRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));

        if (!message.getSender().getId().equals(currentUser.getId())) {
            throw new BadRequestException("You can only edit your own messages");
        }
        if (message.isRecalled()) {
            throw new BadRequestException("Cannot edit a recalled message");
        }

        message.setContent(request.getContent());
        message.setEdited(true);
        message.setEditedAt(LocalDateTime.now());
        Message savedMessage = messageRepository.save(message);

        MessageResponse response = mapToMessageResponse(savedMessage);
        broadcastMessageUpdate(message.getConversation(), response);
        return response;
    }

    @Transactional
    public MessageResponse recallMessage(UUID messageId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));

        if (!message.getSender().getId().equals(currentUser.getId())) {
            throw new BadRequestException("You can only recall your own messages");
        }

        message.setRecalled(true);
        message.setContent("Tin nhắn đã bị thu hồi");
        Message savedMessage = messageRepository.save(message);

        MessageResponse response = mapToMessageResponse(savedMessage);
        broadcastMessageUpdate(message.getConversation(), response);
        return response;
    }

    @Transactional
    public void deleteMessageForMe(UUID messageId) {
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

    @Transactional
    public MessageResponse pinMessage(UUID messageId, boolean pin) {
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

        message.setPinned(pin);
        Message savedMessage = messageRepository.save(message);

        MessageResponse response = mapToMessageResponse(savedMessage);
        broadcastMessageUpdate(message.getConversation(), response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getPinnedMessages(UUID conversationId) {
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

    @Transactional
    public MessageResponse reactToMessage(UUID messageId, ReactMessageRequest request) {
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

    @Transactional(readOnly = true)
    public List<MessageResponse> searchMessages(String query, UUID conversationId) {
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
            List<Conversation> userConversations = conversationRepository.findAllByUserIdOrderByUpdatedAtDesc(currentUser.getId());
            List<UUID> conversationIds = userConversations.stream().map(Conversation::getId).toList();
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
