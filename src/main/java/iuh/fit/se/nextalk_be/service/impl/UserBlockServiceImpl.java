package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.UserBlockService;

import iuh.fit.se.nextalk_be.dto.response.BlockStatusResponse;
import iuh.fit.se.nextalk_be.dto.response.ConversationResponse;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.entity.UserBlock;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.UserBlockRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.ConversationService;
import iuh.fit.se.nextalk_be.service.UserService;


import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserBlockServiceImpl implements UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;

    public BlockStatusResponse block(String userId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        if (currentUser.getId().equals(userId)) {
            throw new BadRequestException("Cannot block yourself");
        }

        User blockedUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (!userBlockRepository.existsByBlockerIdAndBlockedId(currentUser.getId(), userId)) {
            userBlockRepository.save(UserBlock.builder()
                    .blocker(currentUser)
                    .blocked(blockedUser)
                    .build());
            broadcastBlockStatusUpdate(currentUser, blockedUser);
        }

        return getStatus(userId);
    }

    public BlockStatusResponse unblock(String userId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        User blockedUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
        
        userBlockRepository.findByBlockerIdAndBlockedId(currentUser.getId(), userId)
                .ifPresent(block -> {
                    userBlockRepository.delete(block);
                    broadcastBlockStatusUpdate(currentUser, blockedUser);
                });
        return getStatus(userId);
    }

    private void broadcastBlockStatusUpdate(User currentUser, User otherUser) {
        conversationRepository.findPrivateConversationBetweenUsers(new ObjectId(currentUser.getId()), new ObjectId(otherUser.getId()))
                .ifPresent(conversation -> {
                    ConversationResponse res1 = conversationService.mapToConversationResponse(conversation, currentUser);
                    Map<String, Object> payload1 = new HashMap<>();
                    payload1.put("type", "CONVERSATION_UPDATE");
                    payload1.put("data", res1);
                    messagingTemplate.convertAndSendToUser(currentUser.getUsername(), "/queue/private", payload1);

                    ConversationResponse res2 = conversationService.mapToConversationResponse(conversation, otherUser);
                    Map<String, Object> payload2 = new HashMap<>();
                    payload2.put("type", "CONVERSATION_UPDATE");
                    payload2.put("data", res2);
                    messagingTemplate.convertAndSendToUser(otherUser.getUsername(), "/queue/private", payload2);
                });
    }

    public BlockStatusResponse getStatus(String userId) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        if (currentUser.getId().equals(userId)) {
            return BlockStatusResponse.builder()
                    .userId(userId)
                    .blockedByMe(false)
                    .blockedMe(false)
                    .blocked(false)
                    .build();
        }

        boolean blockedByMe = userBlockRepository.existsByBlockerIdAndBlockedId(currentUser.getId(), userId);
        boolean blockedMe = userBlockRepository.existsByBlockerIdAndBlockedId(userId, currentUser.getId());
        return BlockStatusResponse.builder()
                .userId(userId)
                .blockedByMe(blockedByMe)
                .blockedMe(blockedMe)
                .blocked(blockedByMe || blockedMe)
                .build();
    }
}
