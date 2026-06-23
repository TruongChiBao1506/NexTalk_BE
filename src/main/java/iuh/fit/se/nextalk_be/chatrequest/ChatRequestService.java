package iuh.fit.se.nextalk_be.chatrequest;

import iuh.fit.se.nextalk_be.block.UserBlockRepository;
import iuh.fit.se.nextalk_be.channel.ChannelRepository;
import iuh.fit.se.nextalk_be.chatrequest.dto.ChatRequestResponse;
import iuh.fit.se.nextalk_be.chatrequest.dto.CreateChatRequest;
import iuh.fit.se.nextalk_be.conversation.Conversation;
import iuh.fit.se.nextalk_be.conversation.ConversationRepository;
import iuh.fit.se.nextalk_be.conversation.ConversationType;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.friend.FriendshipRepository;
import iuh.fit.se.nextalk_be.friend.FriendshipStatus;
import iuh.fit.se.nextalk_be.message.Message;
import iuh.fit.se.nextalk_be.message.MessageRepository;
import iuh.fit.se.nextalk_be.message.MessageStatus;
import iuh.fit.se.nextalk_be.message.MessageStatusRepository;
import iuh.fit.se.nextalk_be.message.MessageType;
import iuh.fit.se.nextalk_be.notification.NotificationService;
import iuh.fit.se.nextalk_be.notification.NotificationType;
import iuh.fit.se.nextalk_be.user.User;
import iuh.fit.se.nextalk_be.user.UserRepository;
import iuh.fit.se.nextalk_be.user.UserService;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatRequestService {
    private static final int DAILY_STRANGER_MESSAGE_LIMIT = 10;

    private final ChatRequestRepository chatRequestRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageStatusRepository messageStatusRepository;
    private final NotificationService notificationService;
    private final UserBlockRepository userBlockRepository;
    private final ChannelRepository channelRepository;

    public ChatRequestResponse create(CreateChatRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        if (currentUser.getId().equals(request.getReceiverId())) {
            throw new BadRequestException("Cannot send a chat request to yourself");
        }

        User receiver = userRepository.findById(request.getReceiverId())
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found"));

        if (userBlockRepository.existsBetweenUsers(currentUser.getId(), receiver.getId())) {
            throw new BadRequestException("Cannot send a chat request because one of you has blocked the other");
        }

        boolean alreadyFriends = friendshipRepository
                .findBySenderIdAndReceiverIdAndStatus(currentUser.getId(), receiver.getId(), FriendshipStatus.ACCEPTED)
                .isPresent()
                || friendshipRepository
                .findBySenderIdAndReceiverIdAndStatus(receiver.getId(), currentUser.getId(), FriendshipStatus.ACCEPTED)
                .isPresent();
        if (alreadyFriends) {
            throw new BadRequestException("You are already friends. Start a normal conversation instead");
        }

        chatRequestRepository.findBySenderIdAndReceiverIdAndStatus(
                currentUser.getId(), receiver.getId(), ChatRequestStatus.PENDING
        ).ifPresent(existing -> {
            throw new BadRequestException("You already sent a pending chat request to this user");
        });

        long sentToday = chatRequestRepository.countBySenderIdAndCreatedAtAfter(
                currentUser.getId(),
                LocalDateTime.now().toLocalDate().atStartOfDay()
        );
        if (sentToday >= DAILY_STRANGER_MESSAGE_LIMIT) {
            throw new BadRequestException("Daily stranger message limit reached. Please try again tomorrow");
        }

        Message sharedMessage = null;
        if (request.getSharedMessageId() != null && !request.getSharedMessageId().isBlank()) {
            sharedMessage = messageRepository.findById(request.getSharedMessageId())
                    .orElseThrow(() -> new ResourceNotFoundException("Shared message not found"));
            boolean canReadSharedMessage = sharedMessage.getConversation().getMembers().stream()
                    .anyMatch(member -> member.getId().equals(currentUser.getId()));
            if (!canReadSharedMessage || sharedMessage.isRecalled()
                    || sharedMessage.getMessageType() == MessageType.SYSTEM
                    || sharedMessage.getMessageType() == MessageType.POLL) {
                throw new BadRequestException("This message cannot be shared");
            }
            if (channelRepository.findByConversationId(sharedMessage.getConversation().getId())
                    .map(channel -> channel.isPrivate())
                    .orElse(false)) {
                throw new BadRequestException("Messages from private channels cannot be shared to strangers");
            }
        }
        if (sharedMessage == null && (request.getMessage() == null || request.getMessage().isBlank())) {
            throw new BadRequestException("Message is required");
        }

        ChatRequest saved = chatRequestRepository.save(ChatRequest.builder()
                .sender(currentUser)
                .receiver(receiver)
                .message(toPlainText(request.getMessage()))
                .sharedMessageId(sharedMessage != null ? sharedMessage.getId() : null)
                .sharedFromSenderUsername(sharedMessage != null ? sharedMessage.getSender().getUsername() : null)
                .sharedMessageType(sharedMessage != null ? sharedMessage.getMessageType() : null)
                .sharedAttachments(sharedMessage != null && sharedMessage.getAttachments() != null
                        ? new ArrayList<>(sharedMessage.getAttachments())
                        : new ArrayList<>())
                .status(ChatRequestStatus.PENDING)
                .build());

        notificationService.createAndSend(
                receiver,
                NotificationType.CHAT_REQUEST,
                currentUser.getUsername() + " sent you a chat request",
                saved.getId()
        );

        return mapToResponse(saved, null);
    }

    public List<ChatRequestResponse> getIncomingPending() {
        User currentUser = userService.getCurrentAuthenticatedUser();
        return chatRequestRepository.findByReceiverIdAndStatusOrderByCreatedAtDesc(
                        currentUser.getId(), ChatRequestStatus.PENDING
                )
                .stream()
                .map(request -> mapToResponse(request, null))
                .toList();
    }

    public List<ChatRequestResponse> getOutgoingPending() {
        User currentUser = userService.getCurrentAuthenticatedUser();
        return chatRequestRepository.findBySenderIdAndStatusOrderByCreatedAtDesc(
                        currentUser.getId(), ChatRequestStatus.PENDING
                )
                .stream()
                .map(request -> mapToResponse(request, null))
                .toList();
    }

    public ChatRequestResponse accept(String id) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        ChatRequest request = chatRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chat request not found"));

        if (!request.getReceiver().getId().equals(currentUser.getId())) {
            throw new UnauthorizedException("Only the receiver can accept this chat request");
        }
        if (request.getStatus() != ChatRequestStatus.PENDING) {
            throw new BadRequestException("Chat request is no longer pending");
        }

        Conversation conversation = conversationRepository.findPrivateConversationBetweenUsers(
                        new ObjectId(request.getSender().getId()),
                        new ObjectId(request.getReceiver().getId())
                )
                .orElseGet(() -> conversationRepository.save(Conversation.builder()
                        .type(ConversationType.PRIVATE)
                        .members(Set.of(request.getSender(), request.getReceiver()))
                        .build()));

        Message firstMessage = messageRepository.save(Message.builder()
                .conversation(conversation)
                .sender(request.getSender())
                .content(request.getMessage())
                .messageType(request.getSharedMessageType() != null ? request.getSharedMessageType() : MessageType.TEXT)
                .attachments(request.getSharedAttachments() != null ? request.getSharedAttachments() : new ArrayList<>())
                .forwardedFromMessageId(request.getSharedMessageId())
                .forwardedFromSenderUsername(request.getSharedFromSenderUsername())
                .build());

        messageStatusRepository.save(MessageStatus.builder()
                .message(firstMessage)
                .user(request.getReceiver())
                .status("SEEN")
                .build());

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        request.setStatus(ChatRequestStatus.ACCEPTED);
        ChatRequest saved = chatRequestRepository.save(request);

        return mapToResponse(saved, conversation.getId());
    }

    public ChatRequestResponse reject(String id) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        ChatRequest request = chatRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chat request not found"));

        if (!request.getReceiver().getId().equals(currentUser.getId())) {
            throw new UnauthorizedException("Only the receiver can reject this chat request");
        }
        if (request.getStatus() != ChatRequestStatus.PENDING) {
            throw new BadRequestException("Chat request is no longer pending");
        }

        request.setStatus(ChatRequestStatus.REJECTED);
        return mapToResponse(chatRequestRepository.save(request), null);
    }

    public ChatRequestResponse cancel(String id) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        ChatRequest request = chatRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chat request not found"));

        if (!request.getSender().getId().equals(currentUser.getId())) {
            throw new UnauthorizedException("Only the sender can cancel this chat request");
        }
        if (request.getStatus() != ChatRequestStatus.PENDING) {
            throw new BadRequestException("Chat request is no longer pending");
        }

        request.setStatus(ChatRequestStatus.CANCELED);
        return mapToResponse(chatRequestRepository.save(request), null);
    }

    private ChatRequestResponse mapToResponse(ChatRequest request, String conversationId) {
        return ChatRequestResponse.builder()
                .id(request.getId())
                .sender(userService.mapToProfileResponse(request.getSender()))
                .receiver(userService.mapToProfileResponse(request.getReceiver()))
                .message(request.getMessage())
                .status(request.getStatus().name())
                .conversationId(conversationId)
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }

    private String toPlainText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
