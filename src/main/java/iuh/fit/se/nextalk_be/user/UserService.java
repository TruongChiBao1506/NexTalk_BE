package iuh.fit.se.nextalk_be.user;

import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.user.dto.ChangePasswordRequest;
import iuh.fit.se.nextalk_be.user.dto.UpdateProfileRequest;
import iuh.fit.se.nextalk_be.user.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import iuh.fit.se.nextalk_be.presence.PresenceService;
import iuh.fit.se.nextalk_be.presence.dto.PresenceUpdateResponse;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import iuh.fit.se.nextalk_be.conversation.ConversationRepository;
import iuh.fit.se.nextalk_be.conversation.Conversation;
import iuh.fit.se.nextalk_be.message.MessageRepository;
import iuh.fit.se.nextalk_be.message.Message;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PresenceService presenceService;
    private final SimpMessageSendingOperations messagingTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("User is not authenticated");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            return userRepository.findById(((User) principal).getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
        }

        String identifier = authentication.getName();
        return userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    public UserProfileResponse getMyProfile() {
        User user = getCurrentAuthenticatedUser();
        return mapToProfileResponse(user);
    }

    public UserProfileResponse getUserProfileById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
        return mapToProfileResponse(user);
    }

    public java.util.List<UserProfileResponse> searchUsersByQuery(String query) {
        String trimmedQuery = query.trim();
        if (trimmedQuery.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(trimmedQuery, trimmedQuery)
                .stream()
                .map(this::mapToProfileResponse)
                .toList();
    }

    // @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        User currentUser = getCurrentAuthenticatedUser();

        if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            String newUsername = request.getUsername().trim();
            if (!newUsername.equals(currentUser.getUsername())) {
                if (userRepository.existsByUsername(newUsername)) {
                    throw new BadRequestException("Username is already taken");
                }
                currentUser.setUsername(newUsername);
            }
        }

        if (request.getAvatarUrl() != null) {
            currentUser.setAvatarUrl(request.getAvatarUrl());
        }

        if (request.getBio() != null) {
            currentUser.setBio(request.getBio());
        }

        User savedUser = userRepository.save(currentUser);
        return mapToProfileResponse(savedUser);
    }

    // @Transactional
    public UserProfileResponse updatePresenceStatus(String statusStr) {
        String status = statusStr.trim().toUpperCase();
        if (!"ONLINE".equals(status) && !"AWAY".equals(status) && !"OFFLINE".equals(status)) {
            throw new BadRequestException("Invalid status. Must be ONLINE, AWAY, or OFFLINE");
        }

        User user = getCurrentAuthenticatedUser();
        presenceService.setUserStatus(user.getId(), status);

        LocalDateTime lastSeen = null;
        if ("OFFLINE".equals(status)) {
            lastSeen = LocalDateTime.now();
            presenceService.setLastSeen(user.getId(), lastSeen);
        }

        // Broadcast presence update
        PresenceUpdateResponse response = PresenceUpdateResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .status(status)
                .lastSeen(lastSeen != null ? lastSeen : LocalDateTime.now())
                .build();
        messagingTemplate.convertAndSend("/topic/presence", response);

        return mapToProfileResponse(user);
    }

    public void changePassword(ChangePasswordRequest request) {
        User user = getCurrentAuthenticatedUser();
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Mật khẩu hiện tại không chính xác");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public UserProfileResponse setupChatPin(String pin) {
        if (pin == null || !pin.matches("\\d{4}")) {
            throw new BadRequestException("PIN must be exactly 4 digits");
        }
        User user = getCurrentAuthenticatedUser();
        if (user.getChatPin() != null && !user.getChatPin().isEmpty()) {
            throw new BadRequestException("Chat PIN is already set. Reset it first if forgotten.");
        }
        user.setChatPin(passwordEncoder.encode(pin));
        return mapToProfileResponse(userRepository.save(user));
    }

    public UserProfileResponse resetChatPin(String pin) {
        User user = getCurrentAuthenticatedUser();

        // 1. Find all conversations hidden by this user
        List<Conversation> hiddenConversations = conversationRepository.findAllByHiddenByUsersContaining(user.getId());

        if (pin != null && !pin.isEmpty()) {
            // "Nhớ mã cũ" flow: Validate PIN
            if (user.getChatPin() == null || !passwordEncoder.matches(pin, user.getChatPin())) {
                throw new BadRequestException("Mã PIN không chính xác");
            }
            
            // Unhide conversations without deleting messages
            for (Conversation conv : hiddenConversations) {
                if (conv.getHiddenByUsers() != null) {
                    conv.getHiddenByUsers().remove(user.getId());
                }
                conversationRepository.save(conv);
            }
        } else {
            // "Quên mã cũ" flow: Delete messages and unhide
            for (Conversation conv : hiddenConversations) {
                List<Message> messages = messageRepository.findAllByConversationId(conv.getId());
                for (Message msg : messages) {
                    if (msg.getDeletedByUsers() == null) {
                        msg.setDeletedByUsers(new ArrayList<>());
                    }
                    if (!msg.getDeletedByUsers().contains(user.getId())) {
                        msg.getDeletedByUsers().add(user.getId());
                    }
                }
                messageRepository.saveAll(messages);

                // Remove from hidden list
                if (conv.getHiddenByUsers() != null) {
                    conv.getHiddenByUsers().remove(user.getId());
                }
                conversationRepository.save(conv);
            }
        }

        // 3. Clear user PIN
        user.setChatPin(null);
        return mapToProfileResponse(userRepository.save(user));
    }

    public UserProfileResponse mapToProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .status(presenceService.getUserStatus(user.getId()))
                .lastSeen(presenceService.getUserLastSeen(user.getId()))
                .isVerified(user.isVerified())
                .hasChatPin(user.getChatPin() != null && !user.getChatPin().isEmpty())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
