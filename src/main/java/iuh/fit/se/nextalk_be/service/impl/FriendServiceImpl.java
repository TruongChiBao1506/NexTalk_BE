package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.FriendService;

import iuh.fit.se.nextalk_be.dto.request.ChatRequestStatus;
import iuh.fit.se.nextalk_be.dto.response.FriendRelationStatusResponse;
import iuh.fit.se.nextalk_be.dto.response.FriendResponse;
import iuh.fit.se.nextalk_be.dto.response.FriendSuggestionResponse;
import iuh.fit.se.nextalk_be.dto.response.FriendshipAcceptResponse;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.Friendship;
import iuh.fit.se.nextalk_be.entity.FriendshipStatus;
import iuh.fit.se.nextalk_be.entity.FriendSuggestionDismissal;
import iuh.fit.se.nextalk_be.entity.GroupMember;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.entity.NotificationType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.ChatRequestRepository;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.FriendshipRepository;
import iuh.fit.se.nextalk_be.repository.FriendSuggestionDismissalRepository;
import iuh.fit.se.nextalk_be.repository.GroupMemberRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.UserBlockRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.NotificationService;
import iuh.fit.se.nextalk_be.service.PresenceService;
import iuh.fit.se.nextalk_be.service.UserService;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final FriendshipRepository friendshipRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PresenceService presenceService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRequestRepository chatRequestRepository;
    private final UserBlockRepository userBlockRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final FriendSuggestionDismissalRepository friendSuggestionDismissalRepository;

    @Transactional
    public void sendFriendRequest(String receiverId) {
        User currentUser = userService.getCurrentAuthenticatedUser();

        if (currentUser.getId().equals(receiverId)) {
            throw new BadRequestException("Cannot send friend request to yourself");
        }

        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + receiverId));

        if (receiver.isSystemAccount()) {
            throw new BadRequestException("System accounts cannot receive friend requests");
        }

        if (userBlockRepository.existsBetweenUsers(currentUser.getId(), receiverId)) {
            throw new BadRequestException("Cannot send friend request because one of you has blocked the other");
        }

        Optional<Friendship> existing = friendshipRepository.findFriendshipBetweenUsers(currentUser.getId(), receiverId);

        if (existing.isPresent()) {
            Friendship friendship = existing.get();
            switch (friendship.getStatus()) {
                case ACCEPTED:
                    throw new BadRequestException("You are already friends with this user");
                case PENDING:
                    if (friendship.getSender().getId().equals(currentUser.getId())) {
                        throw new BadRequestException("Friend request already sent and is pending");
                    } else {
                        throw new BadRequestException("This user has already sent you a friend request. Please accept it instead.");
                    }
                case REJECTED:
                    // Allow resending the request by updating the existing record
                    friendship.setSender(currentUser);
                    friendship.setReceiver(receiver);
                    friendship.setStatus(FriendshipStatus.PENDING);
                    friendshipRepository.save(friendship);
                    notificationService.createAndSend(
                            receiver,
                            NotificationType.FRIEND_REQUEST,
                            currentUser.getUsername() + " đã gửi lời mời kết bạn",
                            friendship.getId().toString()
                    );
                    return;
                case BLOCKED:
                    throw new BadRequestException("Friend request cannot be sent");
            }
        } else {
            Friendship friendship = Friendship.builder()
                    .sender(currentUser)
                    .receiver(receiver)
                    .status(FriendshipStatus.PENDING)
                    .build();
            Friendship saved = friendshipRepository.save(friendship);
            notificationService.createAndSend(
                    receiver,
                    NotificationType.FRIEND_REQUEST,
                    currentUser.getUsername() + " đã gửi lời mời kết bạn",
                    saved.getId().toString()
            );
        }
    }

    @Transactional
    public FriendshipAcceptResponse acceptFriendRequest(String senderId) {
        User currentUser = userService.getCurrentAuthenticatedUser();

        Friendship friendship = friendshipRepository.findBySenderIdAndReceiverIdAndStatus(senderId, currentUser.getId(), FriendshipStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("No pending friend request from this user found"));

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);

        User sender = friendship.getSender();
        Conversation conversation = conversationRepository.findPrivateConversationBetweenUsers(
                        new ObjectId(sender.getId()),
                        new ObjectId(currentUser.getId())
                )
                .orElseGet(() -> conversationRepository.save(Conversation.builder()
                        .type(ConversationType.PRIVATE)
                        .members(Set.of(sender, currentUser))
                        .build()));

        Message systemMessage = messageRepository.save(Message.builder()
                .conversation(conversation)
                .sender(currentUser)
                .content("đã trở thành bạn bè.")
                .messageType(MessageType.SYSTEM)
                .build());

        broadcastSystemMessage(conversation, systemMessage, currentUser);

        return FriendshipAcceptResponse.builder()
                .conversationId(conversation.getId())
                .build();
    }

    @Transactional
    public void rejectFriendRequest(String senderId) {
        User currentUser = userService.getCurrentAuthenticatedUser();

        Friendship friendship = friendshipRepository.findBySenderIdAndReceiverIdAndStatus(senderId, currentUser.getId(), FriendshipStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("No pending friend request from this user found"));

        // Delete the request to allow them to request again in the future
        friendshipRepository.delete(friendship);
    }

    @Transactional
    public void cancelFriendRequest(String receiverId) {
        User currentUser = userService.getCurrentAuthenticatedUser();

        Friendship friendship = friendshipRepository.findBySenderIdAndReceiverIdAndStatus(currentUser.getId(), receiverId, FriendshipStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("No pending friend request to this user found"));

        friendshipRepository.delete(friendship);
    }

    @Transactional
    public void removeFriend(String friendId) {
        User currentUser = userService.getCurrentAuthenticatedUser();

        Friendship friendship = friendshipRepository.findRelation(currentUser.getId(), friendId)
                .orElseThrow(() -> new ResourceNotFoundException("No friend relation found with this user"));

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new BadRequestException("You are not friends with this user");
        }

        friendshipRepository.delete(friendship);
        chatRequestRepository
                .findBySenderIdAndReceiverIdAndStatus(currentUser.getId(), friendId, ChatRequestStatus.ACCEPTED)
                .ifPresent(chatRequestRepository::delete);
        chatRequestRepository
                .findBySenderIdAndReceiverIdAndStatus(friendId, currentUser.getId(), ChatRequestStatus.ACCEPTED)
                .ifPresent(chatRequestRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> getFriendsList() {
        User currentUser = userService.getCurrentAuthenticatedUser();

        List<Friendship> friendships = friendshipRepository.findAllByUserIdAndStatus(currentUser.getId(), FriendshipStatus.ACCEPTED);

        return friendships.stream()
                .limit(500)
                .map(f -> {
                    User friend = f.getSender().getId().equals(currentUser.getId()) ? f.getReceiver() : f.getSender();
                    return mapToFriendResponse(friend);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> getPendingRequests() {
        User currentUser = userService.getCurrentAuthenticatedUser();

        List<Friendship> pending = friendshipRepository.findByReceiverIdAndStatus(currentUser.getId(), FriendshipStatus.PENDING);

        return pending.stream()
                .limit(500)
                .map(f -> mapToFriendResponse(f.getSender()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FriendRelationStatusResponse getFriendRelationStatus(String targetUserId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        String currentUserId = currentUser.getId();

        if (currentUserId.equals(targetUserId)) {
            return FriendRelationStatusResponse.builder()
                    .userId(targetUserId)
                    .status("SELF")
                    .build();
        }

        userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + targetUserId));

        if (userBlockRepository.existsBetweenUsers(currentUserId, targetUserId)) {
            return FriendRelationStatusResponse.builder()
                    .userId(targetUserId)
                    .status("BLOCKED")
                    .build();
        }

        String status = friendshipRepository.findFriendshipBetweenUsers(currentUserId, targetUserId)
                .map(friendship -> {
                    if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
                        return "FRIENDS";
                    }
                    if (friendship.getStatus() == FriendshipStatus.PENDING) {
                        return friendship.getSender().getId().equals(currentUserId)
                                ? "OUTGOING_PENDING"
                                : "INCOMING_PENDING";
                    }
                    return friendship.getStatus().name();
                })
                .orElse("NONE");

        return FriendRelationStatusResponse.builder()
                .userId(targetUserId)
                .status(status)
                .build();
    }

    @Transactional(readOnly = true)
    public List<FriendSuggestionResponse> getFriendSuggestions(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        User currentUser = userService.getCurrentAuthenticatedUser();
        String currentUserId = currentUser.getId();
        LocalDateTime now = LocalDateTime.now();

        List<Friendship> myAcceptedFriendships = friendshipRepository.findAllByUserIdAndStatus(currentUserId, FriendshipStatus.ACCEPTED);
        Set<String> myFriendIds = myAcceptedFriendships.stream()
                .map(f -> f.getSender().getId().equals(currentUserId) ? f.getReceiver().getId() : f.getSender().getId())
                .collect(Collectors.toSet());

        List<Friendship> mySentRequests = friendshipRepository.findBySenderIdAndStatus(currentUserId, FriendshipStatus.PENDING);
        Set<String> sentRequestUserIds = mySentRequests.stream()
                .map(f -> f.getReceiver().getId())
                .collect(Collectors.toSet());

        List<Friendship> myReceivedRequests = friendshipRepository.findByReceiverIdAndStatus(currentUserId, FriendshipStatus.PENDING);
        Set<String> receivedRequestUserIds = myReceivedRequests.stream()
                .map(f -> f.getSender().getId())
                .collect(Collectors.toSet());

        Set<String> blockedUserIds = userBlockRepository
                .findAllByBlockerIdOrBlockedId(currentUserId, currentUserId)
                .stream()
                .map(block -> block.getBlocker().getId().equals(currentUserId)
                        ? block.getBlocked().getId()
                        : block.getBlocker().getId())
                .collect(Collectors.toSet());

        Set<String> dismissedUserIds = friendSuggestionDismissalRepository
                .findAllByUserIdAndExpiresAtAfter(currentUserId, now)
                .stream()
                .map(FriendSuggestionDismissal::getCandidateUserId)
                .collect(Collectors.toSet());

        Set<String> excludedUserIds = new HashSet<>();
        excludedUserIds.add(currentUserId);
        excludedUserIds.addAll(myFriendIds);
        excludedUserIds.addAll(receivedRequestUserIds);
        excludedUserIds.addAll(sentRequestUserIds);
        excludedUserIds.addAll(blockedUserIds);
        excludedUserIds.addAll(dismissedUserIds);

        Map<String, SuggestionCandidate> candidates = new HashMap<>();

        if (!myFriendIds.isEmpty()) {
            List<Friendship> friendNetwork = friendshipRepository
                    .findBySenderIdInAndStatusOrReceiverIdInAndStatus(
                            myFriendIds,
                            FriendshipStatus.ACCEPTED,
                            myFriendIds,
                            FriendshipStatus.ACCEPTED,
                            PageRequest.of(0, 1_000));

            for (Friendship friendship : friendNetwork) {
                User sender = friendship.getSender();
                User receiver = friendship.getReceiver();
                User candidate = myFriendIds.contains(sender.getId()) ? receiver : sender;
                if (!isEligibleCandidate(candidate, excludedUserIds)) {
                    continue;
                }
                candidates.computeIfAbsent(candidate.getId(), ignored -> new SuggestionCandidate(candidate))
                        .mutualFriendsCount++;
            }
        }

        List<GroupMember> myMemberships = groupMemberRepository.findAllByUserId(currentUserId);
        List<String> sharedGroupIds = myMemberships.stream()
                .map(GroupMember::getGroup)
                .filter(java.util.Objects::nonNull)
                .map(group -> group.getId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(25)
                .toList();

        if (!sharedGroupIds.isEmpty()) {
            List<ObjectId> objectIds = sharedGroupIds.stream()
                    .filter(ObjectId::isValid)
                    .map(ObjectId::new)
                    .toList();
            List<GroupMember> sharedGroupMembers = groupMemberRepository
                    .findFriendSuggestionCandidatesByGroupIdIn(
                            sharedGroupIds,
                            objectIds,
                            PageRequest.of(0, 1_000));
            for (GroupMember membership : sharedGroupMembers) {
                User candidate = membership.getUser();
                if (!isEligibleCandidate(candidate, excludedUserIds)) {
                    continue;
                }
                candidates.computeIfAbsent(candidate.getId(), ignored -> new SuggestionCandidate(candidate))
                        .sharedGroupsCount++;
            }
        }

        if (candidates.size() < limit) {
            List<User> discoveryCandidates = userRepository.findFriendSuggestionDiscoveryCandidates(
                    PageRequest.of(0, Math.max(50, limit * 3), Sort.by(Sort.Direction.DESC, "createdAt")));
            for (User candidate : discoveryCandidates) {
                if (isEligibleCandidate(candidate, excludedUserIds)) {
                    candidates.putIfAbsent(candidate.getId(), new SuggestionCandidate(candidate));
                }
            }
        }

        List<FriendSuggestionResponse> result = new ArrayList<>();
        mySentRequests.stream()
                .map(Friendship::getReceiver)
                .filter(java.util.Objects::nonNull)
                .filter(user -> !blockedUserIds.contains(user.getId()))
                .map(user -> mapSuggestion(user, 0, 0, "Đã gửi lời mời kết bạn", true))
                .forEach(result::add);

        candidates.values().stream()
                .sorted((left, right) -> {
                    int scoreComparison = Integer.compare(right.score(now), left.score(now));
                    if (scoreComparison != 0) {
                        return scoreComparison;
                    }
                    return Integer.compare(
                            dailyRotationKey(currentUserId, left.user.getId()),
                            dailyRotationKey(currentUserId, right.user.getId()));
                })
                .limit(limit)
                .map(candidate -> mapSuggestion(
                        candidate.user,
                        candidate.mutualFriendsCount,
                        candidate.sharedGroupsCount,
                        candidate.reason(),
                        false))
                .forEach(result::add);

        return result;
    }

    @Transactional
    public void dismissFriendSuggestion(String candidateUserId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        if (currentUser.getId().equals(candidateUserId)) {
            throw new BadRequestException("Cannot dismiss yourself");
        }
        userRepository.findById(candidateUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        FriendSuggestionDismissal dismissal = friendSuggestionDismissalRepository
                .findByUserIdAndCandidateUserId(currentUser.getId(), candidateUserId)
                .orElseGet(() -> FriendSuggestionDismissal.builder()
                        .userId(currentUser.getId())
                        .candidateUserId(candidateUserId)
                        .build());
        dismissal.setExpiresAt(LocalDateTime.now().plusDays(30));
        friendSuggestionDismissalRepository.save(dismissal);
    }

    private boolean isEligibleCandidate(User candidate, Set<String> excludedUserIds) {
        return candidate != null
                && candidate.getId() != null
                && !excludedUserIds.contains(candidate.getId())
                && !candidate.isAccountLocked()
                && !candidate.isSystemAccount()
                && candidate.isFriendSuggestionDiscoverable();
    }

    private FriendSuggestionResponse mapSuggestion(
            User candidate,
            int mutualFriendsCount,
            int sharedGroupsCount,
            String suggestionReason,
            boolean requestSent) {
        return FriendSuggestionResponse.builder()
                .id(candidate.getId())
                .username(candidate.getUsername())
                .avatarUrl(candidate.getAvatarUrl())
                .bio(candidate.getBio())
                .status(candidate.isShowActivityStatus()
                        ? presenceService.getUserStatus(candidate.getId())
                        : "HIDDEN")
                .lastSeen(candidate.isShowActivityStatus()
                        ? presenceService.getUserLastSeen(candidate.getId())
                        : null)
                .mutualFriendsCount(mutualFriendsCount)
                .sharedGroupsCount(sharedGroupsCount)
                .suggestionReason(suggestionReason)
                .isRequestSent(requestSent)
                .build();
    }

    private int dailyRotationKey(String currentUserId, String candidateUserId) {
        return Math.floorMod(
                (currentUserId + ':' + candidateUserId + ':' + LocalDate.now()).hashCode(),
                Integer.MAX_VALUE);
    }

    private static final class SuggestionCandidate {
        private final User user;
        private int mutualFriendsCount;
        private int sharedGroupsCount;

        private SuggestionCandidate(User user) {
            this.user = user;
        }

        private int score(LocalDateTime now) {
            int score = mutualFriendsCount * 100 + sharedGroupsCount * 30;
            if (user.isVerified()) {
                score += 10;
            }
            if (user.getCreatedAt() != null && user.getCreatedAt().isAfter(now.minusDays(30))) {
                score += 5;
            }
            return score;
        }

        private String reason() {
            if (mutualFriendsCount > 0 && sharedGroupsCount > 0) {
                return mutualFriendsCount + " bạn chung · " + sharedGroupsCount + " nhóm chung";
            }
            if (mutualFriendsCount > 0) {
                return mutualFriendsCount + " bạn chung";
            }
            if (sharedGroupsCount > 0) {
                return sharedGroupsCount + " nhóm chung";
            }
            return user.isVerified()
                    ? "Thành viên đã xác minh trên NexTalk"
                    : "Thành viên mới trên NexTalk";
        }
    }

    private FriendResponse mapToFriendResponse(User user) {
        return FriendResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .status(user.isShowActivityStatus() ? presenceService.getUserStatus(user.getId()) : "HIDDEN")
                .lastSeen(user.isShowActivityStatus() ? presenceService.getUserLastSeen(user.getId()) : null)
                .createdAt(user.getCreatedAt())
                .build();
    }

    private void broadcastSystemMessage(Conversation conversation, Message message, User actor) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", message.getId());
        response.put("conversationId", conversation.getId());
        response.put("senderId", actor.getId());
        response.put("senderUsername", actor.getUsername());
        response.put("content", message.getContent());
        response.put("messageType", message.getMessageType().name());
        response.put("attachments", java.util.List.of());
        response.put("statuses", java.util.List.of());
        response.put("createdAt", message.getCreatedAt());
        response.put("reactions", java.util.List.of());

        for (User member : conversation.getMembers()) {
            messagingTemplate.convertAndSendToUser(member.getUsername(), "/queue/private", response);
        }
    }
}
