package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.request.ChangePasswordRequest;
import iuh.fit.se.nextalk_be.dto.request.UpdateProfileRequest;
import iuh.fit.se.nextalk_be.dto.response.PresenceUpdateResponse;
import iuh.fit.se.nextalk_be.dto.response.UserProfileResponse;
import iuh.fit.se.nextalk_be.dto.response.ProfileQrResponse;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;

public interface UserService {
    public User getCurrentAuthenticatedUser();
    public UserProfileResponse getMyProfile();
    public UserProfileResponse getUserProfileById(String id);
    public java.util.List<UserProfileResponse> searchUsersByQuery(String query);
    public UserProfileResponse updateProfile(UpdateProfileRequest request);
    public UserProfileResponse updatePresenceStatus(String statusStr);
    public void changePassword(ChangePasswordRequest request);
    public UserProfileResponse setupChatPin(String pin);
    public UserProfileResponse resetChatPin(String pin);
    public UserProfileResponse mapToProfileResponse(User user);
    ProfileQrResponse getProfileQr();
    ProfileQrResponse rotateProfileQr();
    ProfileQrResponse setProfileQrEnabled(boolean enabled);
    UserProfileResponse resolveProfileQr(String tokenOrLegacyUserId);
}
