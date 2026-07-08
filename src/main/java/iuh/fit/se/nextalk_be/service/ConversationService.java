package iuh.fit.se.nextalk_be.service;

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

public interface ConversationService {
    @Transactional public ConversationResponse getOrCreatePrivateConversation(String friendId);
    public List<ConversationResponse> getUserConversations();
    public ConversationResponse getConversationById(String id);
    public ConversationResponse mapToConversationResponse(Conversation conversation);
    public ConversationResponse updateSelfDestruct(String id, int selfDestructSeconds);
    public ConversationResponse updatePinned(String id, boolean pinned);
    public void deleteForCurrentUser(String id);
    public List<ConversationResponse> searchConversations(String query);
    public ConversationResponse updateHidden(String id, boolean hidden);
    public ConversationResponse updateTheme(String id, iuh.fit.se.nextalk_be.dto.request.UpdateThemeRequest request);
}
