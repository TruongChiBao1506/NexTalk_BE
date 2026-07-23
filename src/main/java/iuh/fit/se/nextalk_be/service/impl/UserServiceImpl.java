package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.UserService;

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
import iuh.fit.se.nextalk_be.repository.RefreshTokenRepository;
import iuh.fit.se.nextalk_be.service.PresenceService;
import iuh.fit.se.nextalk_be.service.WebSocketSessionRegistry;
import iuh.fit.se.nextalk_be.entity.RefreshToken;


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

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PresenceService presenceService;
    private final SimpMessageSendingOperations messagingTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    public User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("User is not authenticated");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            return (User) principal;
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

    public ProfileQrResponse getProfileQr() {
        User user = getCurrentAuthenticatedUser();
        ensureProfileQrToken(user);
        return toProfileQrResponse(user);
    }

    public ProfileQrResponse rotateProfileQr() {
        User user = getCurrentAuthenticatedUser();
        user.setProfileQrToken(newProfileQrToken());
        user.setProfileQrEnabled(true);
        return toProfileQrResponse(userRepository.save(user));
    }

    public ProfileQrResponse setProfileQrEnabled(boolean enabled) {
        User user = getCurrentAuthenticatedUser();
        ensureProfileQrToken(user);
        user.setProfileQrEnabled(enabled);
        return toProfileQrResponse(userRepository.save(user));
    }

    public UserProfileResponse resolveProfileQr(String tokenOrLegacyUserId) {
        User user = userRepository.findByProfileQrToken(tokenOrLegacyUserId)
                // Temporary compatibility for profile QR codes issued before public tokens.
                .or(() -> userRepository.findById(tokenOrLegacyUserId))
                .orElseThrow(() -> new ResourceNotFoundException("Profile QR code is invalid"));
        if (!user.isProfileQrEnabled()) throw new ResourceNotFoundException("Profile QR code is disabled");
        if (user.getProfileQrToken() == null || user.getProfileQrToken().isBlank()) {
            user.setProfileQrToken(newProfileQrToken());
            userRepository.save(user);
        }
        return mapToProfileResponse(user);
    }

    private void ensureProfileQrToken(User user) {
        if (user.getProfileQrToken() == null || user.getProfileQrToken().isBlank()) {
            user.setProfileQrToken(newProfileQrToken());
            userRepository.save(user);
        }
    }

    private String newProfileQrToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private ProfileQrResponse toProfileQrResponse(User user) {
        return ProfileQrResponse.builder()
                .token(user.getProfileQrToken())
                .enabled(user.isProfileQrEnabled())
                .build();
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

        if (request.getBirthday() != null) {
            currentUser.setBirthday(request.getBirthday().isBlank() ? null : request.getBirthday());
        }

        if (request.getEnableBirthdayNotification() != null) {
            currentUser.setEnableBirthdayNotification(request.getEnableBirthdayNotification());
        }

        if (request.getShowActivityStatus() != null) {
            currentUser.setShowActivityStatus(request.getShowActivityStatus());
            String visibleStatus = currentUser.isShowActivityStatus()
                    ? presenceService.getUserStatus(currentUser.getId())
                    : "HIDDEN";
            PresenceUpdateResponse presenceUpdate = PresenceUpdateResponse.builder()
                    .userId(currentUser.getId())
                    .username(currentUser.getUsername())
                    .status(visibleStatus)
                    .lastSeen(currentUser.isShowActivityStatus() ? presenceService.getUserLastSeen(currentUser.getId()) : null)
                    .build();
            messagingTemplate.convertAndSend("/topic/presence", presenceUpdate);
        }

        if (request.getBlockStrangerMessages() != null) {
            currentUser.setBlockStrangerMessages(request.getBlockStrangerMessages());
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
                .status(user.isShowActivityStatus() ? status : "HIDDEN")
                .lastSeen(user.isShowActivityStatus() ? (lastSeen != null ? lastSeen : LocalDateTime.now()) : null)
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
        List<RefreshToken> activeSessions = refreshTokenRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        refreshTokenRepository.deleteByUser(user);
        webSocketSessionRegistry.closeLoginSessions(activeSessions.stream().map(RefreshToken::getId).toList());
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
                .status(user.isShowActivityStatus() ? presenceService.getUserStatus(user.getId()) : "HIDDEN")
                .lastSeen(user.isShowActivityStatus() ? presenceService.getUserLastSeen(user.getId()) : null)
                .showActivityStatus(user.isShowActivityStatus())
                .blockStrangerMessages(user.isBlockStrangerMessages())
                .isVerified(user.isVerified())
                .hasChatPin(user.getChatPin() != null && !user.getChatPin().isEmpty())
                .birthday(user.getBirthday())
                .enableBirthdayNotification(user.isEnableBirthdayNotification())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
