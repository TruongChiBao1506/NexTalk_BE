package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.request.ChatRequestStatus;
import iuh.fit.se.nextalk_be.dto.response.FriendRelationStatusResponse;
import iuh.fit.se.nextalk_be.dto.response.FriendResponse;
import iuh.fit.se.nextalk_be.dto.response.FriendSuggestionResponse;
import iuh.fit.se.nextalk_be.dto.response.FriendshipAcceptResponse;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.Friendship;
import iuh.fit.se.nextalk_be.entity.FriendshipStatus;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.entity.NotificationType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.ChatRequestRepository;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.FriendshipRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public interface FriendService {
    @Transactional public void sendFriendRequest(String receiverId);
    @Transactional public FriendshipAcceptResponse acceptFriendRequest(String senderId);
    @Transactional public void rejectFriendRequest(String senderId);
    @Transactional public void cancelFriendRequest(String receiverId);
    @Transactional public void removeFriend(String friendId);
    @Transactional(readOnly = true) public List<FriendResponse> getFriendsList();
    @Transactional(readOnly = true) public List<FriendResponse> getPendingRequests();
    @Transactional(readOnly = true) public FriendRelationStatusResponse getFriendRelationStatus(String targetUserId);
    @Transactional(readOnly = true) public List<FriendSuggestionResponse> getFriendSuggestions();
}
