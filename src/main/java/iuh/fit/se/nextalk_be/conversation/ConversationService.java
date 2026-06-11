package iuh.fit.se.nextalk_be.conversation;

import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.chatrequest.ChatRequestRepository;
import iuh.fit.se.nextalk_be.chatrequest.ChatRequestStatus;
import iuh.fit.se.nextalk_be.block.UserBlockRepository;
import iuh.fit.se.nextalk_be.conversation.dto.ConversationResponse;
import iuh.fit.se.nextalk_be.friend.FriendshipRepository;
import iuh.fit.se.nextalk_be.friend.FriendshipStatus;
import iuh.fit.se.nextalk_be.user.User;
import iuh.fit.se.nextalk_be.user.UserRepository;
import iuh.fit.se.nextalk_be.user.UserService;
import iuh.fit.se.nextalk_be.user.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final ChatRequestRepository chatRequestRepository;
    private final UserBlockRepository userBlockRepository;

    @Transactional
    public ConversationResponse getOrCreatePrivateConversation(String friendId) {
        User currentUser = userService.getCurrentAuthenticatedUser();

        if (currentUser.getId().equals(friendId)) {
            throw new BadRequestException("Cannot create a private conversation with yourself");
        }

        User friend = userRepository.findById(friendId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + friendId));

        if (userBlockRepository.existsBetweenUsers(currentUser.getId(), friendId)) {
            throw new BadRequestException("Cannot create a private conversation because one of you has blocked the other");
        }

        Optional<Conversation> existing = conversationRepository
                .findAllByMembersIdOrderByUpdatedAtDesc(currentUser.getId())
                .stream()
                .filter(conversation -> conversation.getType() == ConversationType.PRIVATE)
                .filter(conversation -> conversation.getMembers().stream()
                        .map(User::getId)
                        .anyMatch(friendId::equals))
                .findFirst();

        if (existing.isPresent()) {
            return mapToConversationResponse(existing.get());
        }

        Conversation conversation = Conversation.builder()
                .type(ConversationType.PRIVATE)
                .members(new HashSet<>(Arrays.asList(currentUser, friend)))
                .build();

        Conversation savedConversation = conversationRepository.save(conversation);
        return mapToConversationResponse(savedConversation);
    }

    // @Transactional(readOnly = true)
    public List<ConversationResponse> getUserConversations() {
        User currentUser = userService.getCurrentAuthenticatedUser();

        List<Conversation> conversations = conversationRepository.findAllByMembersIdOrderByUpdatedAtDesc(currentUser.getId());
        List<Conversation> deduplicated = deduplicateConversations(conversations, currentUser.getId());

        return deduplicated.stream()
                .map(this::mapToConversationResponse)
                .collect(Collectors.toList());
    }

    private List<Conversation> deduplicateConversations(List<Conversation> conversations, String currentUserId) {
        Map<String, Conversation> latestPrivateConvs = new LinkedHashMap<>();
        List<Conversation> result = new ArrayList<>();

        for (Conversation conv : conversations) {
            if (conv.getType() == ConversationType.PRIVATE) {
                String otherMemberId = conv.getMembers().stream()
                        .map(User::getId)
                        .filter(id -> !id.equals(currentUserId))
                        .findFirst()
                        .orElse(null);

                if (otherMemberId != null) {
                    if (!latestPrivateConvs.containsKey(otherMemberId)) {
                        latestPrivateConvs.put(otherMemberId, conv);
                        result.add(conv);
                    }
                } else {
                    result.add(conv);
                }
            } else {
                result.add(conv);
            }
        }
        return result;
    }

    // @Transactional(readOnly = true)
    public ConversationResponse getConversationById(String id) {
        User currentUser = userService.getCurrentAuthenticatedUser();

        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with ID: " + id));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(m -> m.getId().equals(currentUser.getId()));

        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }

        return mapToConversationResponse(conversation);
    }

    public ConversationResponse mapToConversationResponse(Conversation conversation) {
        Set<UserProfileResponse> memberResponses = conversation.getMembers().stream()
                .map(m -> UserProfileResponse.builder()
                        .id(m.getId())
                        .email(m.getEmail())
                        .username(m.getUsername())
                        .avatarUrl(m.getAvatarUrl())
                        .bio(m.getBio())
                        .status(m.getStatus())
                        .isVerified(m.isVerified())
                        .createdAt(m.getCreatedAt())
                        .updatedAt(m.getUpdatedAt())
                        .build())
                .collect(Collectors.toSet());

        User currentUser = userService.getCurrentAuthenticatedUser();
        String otherMemberId = getPrivateOtherMemberId(conversation, currentUser.getId());
        boolean blockedByMe = otherMemberId != null
                && userBlockRepository.existsByBlockerIdAndBlockedId(currentUser.getId(), otherMemberId);
        boolean blockedMe = otherMemberId != null
                && userBlockRepository.existsByBlockerIdAndBlockedId(otherMemberId, currentUser.getId());

        return ConversationResponse.builder()
                .id(conversation.getId())
                .type(conversation.getType().name())
                .name(conversation.getName())
                .canSendMessages(canSendMessages(conversation))
                .blockedByMe(blockedByMe)
                .blockedMe(blockedMe)
                .members(memberResponses)
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    private String getPrivateOtherMemberId(Conversation conversation, String currentUserId) {
        if (conversation.getType() != ConversationType.PRIVATE) {
            return null;
        }
        return conversation.getMembers().stream()
                .map(User::getId)
                .filter(id -> !id.equals(currentUserId))
                .findFirst()
                .orElse(null);
    }

    private boolean canSendMessages(Conversation conversation) {
        if (conversation.getType() == ConversationType.GROUP) {
            return true;
        }

        User currentUser = userService.getCurrentAuthenticatedUser();
        String otherMemberId = getPrivateOtherMemberId(conversation, currentUser.getId());

        if (otherMemberId == null) {
            return false;
        }

        if (userBlockRepository.existsBetweenUsers(currentUser.getId(), otherMemberId)) {
            return false;
        }

        boolean areFriends = friendshipRepository.findRelation(currentUser.getId(), otherMemberId)
                .map(friendship -> friendship.getStatus() == FriendshipStatus.ACCEPTED)
                .orElse(false);

        boolean hasAcceptedChatRequest = chatRequestRepository
                .findBySenderIdAndReceiverIdAndStatus(currentUser.getId(), otherMemberId, ChatRequestStatus.ACCEPTED)
                .isPresent()
                || chatRequestRepository
                .findBySenderIdAndReceiverIdAndStatus(otherMemberId, currentUser.getId(), ChatRequestStatus.ACCEPTED)
                .isPresent();

        return areFriends || hasAcceptedChatRequest;
    }

    // @Transactional(readOnly = true)
    public List<ConversationResponse> searchConversations(String query) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        String trimmedQuery = query.trim().toLowerCase();
        if (trimmedQuery.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<Conversation> conversations = conversationRepository.findAllByMembersIdOrderByUpdatedAtDesc(currentUser.getId());
        List<Conversation> deduplicated = deduplicateConversations(conversations, currentUser.getId());

        return deduplicated.stream()
                .filter(c -> {
                    if (c.getType() == ConversationType.GROUP) {
                        return c.getName() != null && c.getName().toLowerCase().contains(trimmedQuery);
                    } else {
                        return c.getMembers().stream()
                                .filter(m -> !m.getId().equals(currentUser.getId()))
                                .anyMatch(m -> m.getUsername().toLowerCase().contains(trimmedQuery));
                    }
                })
                .map(this::mapToConversationResponse)
                .collect(Collectors.toList());
    }
}
