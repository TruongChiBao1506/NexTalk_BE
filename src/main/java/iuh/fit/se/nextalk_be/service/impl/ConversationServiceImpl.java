package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.ConversationService;

import iuh.fit.se.nextalk_be.dto.request.ChatRequestStatus;
import iuh.fit.se.nextalk_be.dto.response.ConversationResponse;
import iuh.fit.se.nextalk_be.dto.response.UserProfileResponse;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.FriendshipStatus;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.ChatRequestRepository;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.FriendshipRepository;
import iuh.fit.se.nextalk_be.repository.UserBlockRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.UserService;


import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final ChatRequestRepository chatRequestRepository;
    private final UserBlockRepository userBlockRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChannelRepository channelRepository;

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
            Conversation conversation = existing.get();
            if (conversation.getDeletedByUsers() != null && conversation.getDeletedByUsers().remove(currentUser.getId())) {
                conversation = conversationRepository.save(conversation);
            }
            return mapToConversationResponse(conversation);
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
                .filter(conversation -> conversation.getDeletedByUsers() == null
                        || !conversation.getDeletedByUsers().contains(currentUser.getId()))
                .filter(conversation -> conversation.getHiddenByUsers() == null
                        || !conversation.getHiddenByUsers().contains(currentUser.getId()))
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
        return mapToConversationResponse(conversation, userService.getCurrentAuthenticatedUser());
    }

    public ConversationResponse mapToConversationResponse(Conversation conversation, User currentUser) {
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

        String otherMemberId = getPrivateOtherMemberId(conversation, currentUser.getId());
        boolean blockedByMe = otherMemberId != null
                && userBlockRepository.existsByBlockerIdAndBlockedId(currentUser.getId(), otherMemberId);
        boolean blockedMe = otherMemberId != null
                && userBlockRepository.existsByBlockerIdAndBlockedId(otherMemberId, currentUser.getId());

        // Với GROUP conversation: lấy tên group thực (conversation.name = "Chung" là tên channel mặc định)
        String displayName = conversation.getName();
        if (conversation.getType() == ConversationType.GROUP) {
            displayName = channelRepository.findByConversationId(conversation.getId())
                    .map(ch -> ch.getGroup() != null ? ch.getGroup().getName() : null)
                    .orElse(conversation.getName());
        }

        return ConversationResponse.builder()
                .id(conversation.getId())
                .type(conversation.getType().name())
                .name(displayName)
                .canSendMessages(canSendMessages(conversation))
                .blockedByMe(blockedByMe)
                .blockedMe(blockedMe)
                .pinned(conversation.getPinnedByUsers() != null && conversation.getPinnedByUsers().contains(currentUser.getId()))
                .hidden(conversation.getHiddenByUsers() != null && conversation.getHiddenByUsers().contains(currentUser.getId()))
                .selfDestructSeconds(conversation.getSelfDestructSeconds())
                .themeColor(conversation.getThemeColor())
                .wallpaperUrl(conversation.getWallpaperUrl())
                .members(memberResponses)
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }


    public ConversationResponse updateSelfDestruct(String id, int selfDestructSeconds) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        if (!List.of(0, 300, 3600, 86400).contains(selfDestructSeconds)) {
            throw new BadRequestException("Invalid self destruct duration");
        }

        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with ID: " + id));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(m -> m.getId().equals(currentUser.getId()));
        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }

        conversation.setSelfDestructSeconds(selfDestructSeconds);
        return mapToConversationResponse(conversationRepository.save(conversation));
    }

    public ConversationResponse updatePinned(String id, boolean pinned) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Conversation conversation = getConversationForMember(id, currentUser);

        if (conversation.getPinnedByUsers() == null) {
            conversation.setPinnedByUsers(new HashSet<>());
        }
        if (pinned) {
            conversation.getPinnedByUsers().add(currentUser.getId());
        } else {
            conversation.getPinnedByUsers().remove(currentUser.getId());
        }

        return mapToConversationResponse(conversationRepository.save(conversation));
    }

    public void deleteForCurrentUser(String id) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Conversation conversation = getConversationForMember(id, currentUser);

        if (conversation.getDeletedByUsers() == null) {
            conversation.setDeletedByUsers(new HashSet<>());
        }
        if (conversation.getPinnedByUsers() != null) {
            conversation.getPinnedByUsers().remove(currentUser.getId());
        }
        conversation.getDeletedByUsers().add(currentUser.getId());
        conversationRepository.save(conversation);
    }

    private Conversation getConversationForMember(String id, User currentUser) {
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with ID: " + id));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(m -> m.getId().equals(currentUser.getId()));
        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }
        return conversation;
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
        String trimmedQuery = query.trim();
        if (trimmedQuery.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        // Check if query is a PIN match
        boolean isPinMatch = false;
        if (currentUser.getChatPin() != null && trimmedQuery.length() == 4) {
            if (trimmedQuery.matches("\\d{4}") && passwordEncoder.matches(trimmedQuery, currentUser.getChatPin())) {
                isPinMatch = true;
            }
        }

        List<Conversation> conversations = conversationRepository.findAllByMembersIdOrderByUpdatedAtDesc(currentUser.getId());
        List<Conversation> deduplicated = deduplicateConversations(conversations, currentUser.getId());

        if (isPinMatch) {
            // Return only hidden conversations (deduplicated by group ID)
            List<ConversationResponse> pinResults = new ArrayList<>();
            Set<String> seenGroupIds = new LinkedHashSet<>();
            for (Conversation c : deduplicated) {
                if (c.getDeletedByUsers() != null && c.getDeletedByUsers().contains(currentUser.getId())) continue;
                if (c.getHiddenByUsers() == null || !c.getHiddenByUsers().contains(currentUser.getId())) continue;
                if (c.getType() == ConversationType.GROUP) {
                    String groupId = channelRepository.findByConversationId(c.getId())
                            .map(ch -> ch.getGroup() != null ? ch.getGroup().getId() : null)
                            .orElse(null);
                    if (groupId != null && !seenGroupIds.add(groupId)) continue;
                }
                pinResults.add(mapToConversationResponse(c));
            }
            return pinResults;
        } else {
            // Regular search - exclude hidden conversations, deduplicate GROUP by groupId
            Set<String> seenGroupIds = new LinkedHashSet<>();
            List<ConversationResponse> results = new ArrayList<>();
            for (Conversation c : deduplicated) {
                if (c.getDeletedByUsers() != null && c.getDeletedByUsers().contains(currentUser.getId())) continue;
                if (c.getHiddenByUsers() != null && c.getHiddenByUsers().contains(currentUser.getId())) continue;

                if (c.getType() == ConversationType.GROUP) {
                    // Tra ngược channel → group để lấy tên thực và groupId
                    var channel = channelRepository.findByConversationId(c.getId());
                    String groupName = channel
                            .map(ch -> ch.getGroup() != null ? ch.getGroup().getName() : null)
                            .orElse(c.getName());
                    if (groupName == null || !groupName.toLowerCase().contains(trimmedQuery.toLowerCase())) continue;

                    // Chỉ giữ 1 conversation đại diện mỗi group (tránh trùng lặp do nhiều channel)
                    String groupId = channel
                            .map(ch -> ch.getGroup() != null ? ch.getGroup().getId() : null)
                            .orElse(null);
                    if (groupId != null && !seenGroupIds.add(groupId)) continue;
                } else {
                    boolean match = c.getMembers().stream()
                            .filter(m -> !m.getId().equals(currentUser.getId()))
                            .anyMatch(m -> m.getUsername().toLowerCase().contains(trimmedQuery.toLowerCase()));
                    if (!match) continue;
                }

                results.add(mapToConversationResponse(c));
            }
            return results;
        }
    }

    public ConversationResponse updateHidden(String id, boolean hidden) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Conversation conversation = getConversationForMember(id, currentUser);

        if (conversation.getHiddenByUsers() == null) {
            conversation.setHiddenByUsers(new HashSet<>());
        }

        if (hidden) {
            if (currentUser.getChatPin() == null || currentUser.getChatPin().isEmpty()) {
                throw new BadRequestException("Please set up a chat PIN code first.");
            }
            conversation.getHiddenByUsers().add(currentUser.getId());
        } else {
            conversation.getHiddenByUsers().remove(currentUser.getId());
        }

        return mapToConversationResponse(conversationRepository.save(conversation));
    }

    public ConversationResponse updateTheme(String id, iuh.fit.se.nextalk_be.dto.request.UpdateThemeRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Conversation conversation = getConversationForMember(id, currentUser);
        
        conversation.setThemeColor(request.getThemeColor());
        conversation.setWallpaperUrl(request.getWallpaperUrl());
        
        return mapToConversationResponse(conversationRepository.save(conversation));
    }
}
