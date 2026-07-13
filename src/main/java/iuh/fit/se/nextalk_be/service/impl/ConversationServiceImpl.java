package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.ConversationService;

import iuh.fit.se.nextalk_be.dto.response.ConversationResponse;
import iuh.fit.se.nextalk_be.dto.response.UserProfileResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageResponse;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.FriendshipStatus;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.ChatRequestRepository;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.FriendshipRepository;
import iuh.fit.se.nextalk_be.repository.UserBlockRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.service.UserService;


import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

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
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public ConversationResponse getOrCreatePrivateConversation(String friendId) {
        User currentUser = userService.getCurrentAuthenticatedUser();

        if (currentUser.getId().equals(friendId)) {
            throw new BadRequestException("Cannot create a private conversation with yourself");
        }

        User friend = userRepository.findById(friendId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + friendId));

        Optional<Conversation> existing = conversationRepository
                .findAllByMembersIdOrderByUpdatedAtDesc(currentUser.getId())
                .stream()
                .filter(conversation -> conversation.getType() == ConversationType.PRIVATE)
                .filter(conversation -> conversation.getMembers().stream()
                        .map(User::getId)
                        .anyMatch(friendId::equals))
                .findFirst();

        // Việc chặn chỉ ngăn gửi tin mới; hai bên vẫn được xem lại lịch sử hội thoại.
        if (existing.isPresent()) {
            Conversation conversation = existing.get();
            if (conversation.getDeletedByUsers() != null && conversation.getDeletedByUsers().remove(currentUser.getId())) {
                conversation = conversationRepository.save(conversation);
            }
            return mapToConversationResponse(conversation);
        }

        if (userBlockRepository.existsBetweenUsers(currentUser.getId(), friendId)) {
            throw new BadRequestException("Cannot create a private conversation because one of you has blocked the other");
        }

        boolean areFriends = friendshipRepository.findFriendshipBetweenUsers(currentUser.getId(), friendId)
                .filter(friendship -> friendship.getStatus() == FriendshipStatus.ACCEPTED)
                .isPresent();
        if (friend.isBlockStrangerMessages() && !areFriends) {
            throw new BadRequestException("Người dùng này chỉ nhận tin nhắn từ bạn bè.");
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
                // Opening a profile/search result creates a private conversation shell
                // so the composer has an id. Do not expose that shell in either user's
                // inbox until the first message is actually sent.
                .filter(conversation -> conversation.getType() != ConversationType.PRIVATE
                        || messageRepository.existsByConversationId(conversation.getId()))
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
                        .status(m.isShowActivityStatus() ? m.getStatus() : "HIDDEN")
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
                .muted(conversation.getMutedByUsers() != null && conversation.getMutedByUsers().contains(currentUser.getId()))
                .selfDestructSeconds(conversation.getSelfDestructSeconds())
                .themeColor(conversation.getThemeColor())
                .wallpaperUrl(conversation.getWallpaperUrl())
                .nicknames(conversation.getNicknames() != null ? new HashMap<>(conversation.getNicknames()) : new HashMap<>())
                .members(memberResponses)
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    @Override
    public ConversationResponse updateNickname(String id, String targetUserId, String nickname) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with ID: " + id));

        boolean currentUserIsMember = conversation.getMembers().stream().anyMatch(member -> member.getId().equals(currentUser.getId()));
        boolean targetIsMember = conversation.getMembers().stream().anyMatch(member -> member.getId().equals(targetUserId));
        if (!currentUserIsMember || !targetIsMember) {
            throw new BadRequestException("Both users must be members of the conversation");
        }

        User targetUser = conversation.getMembers().stream()
                .filter(member -> member.getId().equals(targetUserId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found in conversation"));

        String normalized = nickname == null ? "" : nickname.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 40) throw new BadRequestException("Nickname must not exceed 40 characters");
        if (conversation.getNicknames() == null) conversation.setNicknames(new HashMap<>());
        String oldNickname = conversation.getNicknames().getOrDefault(targetUserId, "");
        if (Objects.equals(oldNickname, normalized)) return mapToConversationResponse(conversation);
        if (normalized.isBlank()) conversation.getNicknames().remove(targetUserId);
        else conversation.getNicknames().put(targetUserId, normalized);
        Conversation savedConversation = conversationRepository.save(conversation);
        ConversationResponse conversationResponse = mapToConversationResponse(savedConversation);

        Map<String, Object> updateEvent = new HashMap<>();
        updateEvent.put("type", "CONVERSATION_UPDATE");
        updateEvent.put("data", conversationResponse);
        for (User member : savedConversation.getMembers()) {
            messagingTemplate.convertAndSendToUser(member.getUsername(), "/queue/private", updateEvent);
        }

        String content = buildNicknameSystemContent(currentUser, targetUser, oldNickname, normalized);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("systemType", "NICKNAME_UPDATED");
        metadata.put("actorId", currentUser.getId());
        metadata.put("actorUsername", currentUser.getUsername());
        metadata.put("targetUserId", targetUser.getId());
        metadata.put("targetUsername", targetUser.getUsername());
        metadata.put("oldNickname", oldNickname);
        metadata.put("newNickname", normalized);

        Message savedMessage = messageRepository.save(Message.builder()
                .conversation(savedConversation)
                .conversationId(savedConversation.getId())
                .sender(currentUser)
                .senderId(currentUser.getId())
                .senderUsername(currentUser.getUsername())
                .content(content)
                .messageType(MessageType.SYSTEM)
                .metadata(metadata)
                .build());

        MessageResponse messageResponse = MessageResponse.builder()
                .id(savedMessage.getId())
                .conversationId(savedConversation.getId())
                .senderId(currentUser.getId())
                .senderUsername(currentUser.getUsername())
                .content(content)
                .messageType(MessageType.SYSTEM.name())
                .attachments(Collections.emptyList())
                .statuses(Collections.emptyList())
                .reactions(Collections.emptyList())
                .metadata(metadata)
                .createdAt(savedMessage.getCreatedAt())
                .build();
        for (User member : savedConversation.getMembers()) {
            messagingTemplate.convertAndSendToUser(member.getUsername(), "/queue/private", messageResponse);
        }
        return conversationResponse;
    }

    private String buildNicknameSystemContent(User actor, User target, String oldNickname, String newNickname) {
        boolean self = actor.getId().equals(target.getId());
        String setTargetLabel = self ? "của mình" : "cho " + target.getUsername();
        String deleteTargetLabel = self ? "của bạn" : "của " + target.getUsername();
        if (newNickname.isBlank()) return "đã xóa biệt danh " + deleteTargetLabel + ".";
        if (oldNickname == null || oldNickname.isBlank()) {
            return "đã đặt biệt danh " + setTargetLabel + " là \"" + newNickname + "\".";
        }
        return "đã đổi biệt danh " + deleteTargetLabel + " từ \"" + oldNickname + "\" thành \"" + newNickname + "\".";
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

        User otherMember = userRepository.findById(otherMemberId).orElse(null);
        if (otherMember != null && otherMember.isBlockStrangerMessages() && !areFriends) {
            return false;
        }

        // Stranger messaging is opt-out. A user who has not enabled the privacy
        // setting must remain reachable, even before a chat request is accepted.
        // Keep this response flag aligned with MessageServiceImpl's send guard so
        // clients do not disable the composer while the backend permits the send.
        return !otherMember.isBlockStrangerMessages() || areFriends;
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

    public ConversationResponse updateMuted(String id, boolean muted) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Conversation conversation = getConversationForMember(id, currentUser);
        if (conversation.getMutedByUsers() == null) conversation.setMutedByUsers(new HashSet<>());
        if (muted) conversation.getMutedByUsers().add(currentUser.getId());
        else conversation.getMutedByUsers().remove(currentUser.getId());
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
