package iuh.fit.se.nextalk_be.friend;

import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.friend.dto.FriendResponse;
import iuh.fit.se.nextalk_be.notification.NotificationService;
import iuh.fit.se.nextalk_be.notification.NotificationType;
import iuh.fit.se.nextalk_be.user.User;
import iuh.fit.se.nextalk_be.user.UserRepository;
import iuh.fit.se.nextalk_be.user.UserService;
import iuh.fit.se.nextalk_be.presence.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendshipRepository friendshipRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PresenceService presenceService;

    @Transactional
    public void sendFriendRequest(UUID receiverId) {
        User currentUser = userService.getCurrentAuthenticatedUser();

        if (currentUser.getId().equals(receiverId)) {
            throw new BadRequestException("Cannot send friend request to yourself");
        }

        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + receiverId));

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
    public void acceptFriendRequest(UUID senderId) {
        User currentUser = userService.getCurrentAuthenticatedUser();

        Friendship friendship = friendshipRepository.findBySenderIdAndReceiverIdAndStatus(senderId, currentUser.getId(), FriendshipStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("No pending friend request from this user found"));

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
    }

    @Transactional
    public void rejectFriendRequest(UUID senderId) {
        User currentUser = userService.getCurrentAuthenticatedUser();

        Friendship friendship = friendshipRepository.findBySenderIdAndReceiverIdAndStatus(senderId, currentUser.getId(), FriendshipStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("No pending friend request from this user found"));

        // Delete the request to allow them to request again in the future
        friendshipRepository.delete(friendship);
    }

    @Transactional
    public void removeFriend(UUID friendId) {
        User currentUser = userService.getCurrentAuthenticatedUser();

        Friendship friendship = friendshipRepository.findRelation(currentUser.getId(), friendId)
                .orElseThrow(() -> new ResourceNotFoundException("No friend relation found with this user"));

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new BadRequestException("You are not friends with this user");
        }

        friendshipRepository.delete(friendship);
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> getFriendsList() {
        User currentUser = userService.getCurrentAuthenticatedUser();

        List<Friendship> friendships = friendshipRepository.findAllByUserIdAndStatus(currentUser.getId(), FriendshipStatus.ACCEPTED);

        return friendships.stream()
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
                .map(f -> mapToFriendResponse(f.getSender()))
                .collect(Collectors.toList());
    }

    private FriendResponse mapToFriendResponse(User user) {
        return FriendResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .status(presenceService.getUserStatus(user.getId()))
                .lastSeen(presenceService.getUserLastSeen(user.getId()))
                .createdAt(user.getCreatedAt())
                .build();
    }
}
