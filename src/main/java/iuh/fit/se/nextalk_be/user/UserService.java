package iuh.fit.se.nextalk_be.user;

import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
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
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PresenceService presenceService;
    private final SimpMessageSendingOperations messagingTemplate;

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
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
