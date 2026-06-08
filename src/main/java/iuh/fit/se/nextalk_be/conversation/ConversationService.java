package iuh.fit.se.nextalk_be.conversation;

import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.conversation.dto.ConversationResponse;
import iuh.fit.se.nextalk_be.user.User;
import iuh.fit.se.nextalk_be.user.UserRepository;
import iuh.fit.se.nextalk_be.user.UserService;
import iuh.fit.se.nextalk_be.user.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final UserService userService;
    private final UserRepository userRepository;

    @Transactional
    public ConversationResponse getOrCreatePrivateConversation(UUID friendId) {
        User currentUser = userService.getCurrentAuthenticatedUser();

        if (currentUser.getId().equals(friendId)) {
            throw new BadRequestException("Cannot create a private conversation with yourself");
        }

        User friend = userRepository.findById(friendId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + friendId));

        Optional<Conversation> existing = conversationRepository.findPrivateConversationBetweenUsers(currentUser.getId(), friendId);

        if (existing.isPresent()) {
            return mapToConversationResponse(existing.get());
        }

        Conversation conversation = Conversation.builder()
                .type(ConversationType.PRIVATE)
                .members(Set.of(currentUser, friend))
                .build();

        Conversation savedConversation = conversationRepository.save(conversation);
        return mapToConversationResponse(savedConversation);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getUserConversations() {
        User currentUser = userService.getCurrentAuthenticatedUser();

        List<Conversation> conversations = conversationRepository.findAllByUserIdOrderByUpdatedAtDesc(currentUser.getId());

        return conversations.stream()
                .map(this::mapToConversationResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversationById(UUID id) {
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
        Set<UserProfileResponse> memberResponses = conversation.getMembers().stream()
                .map(m -> UserProfileResponse.builder()
                        .id(m.getId())
                        .email(m.getEmail())
                        .username(m.getUsername())
                        .avatarUrl(m.getAvatarUrl())
                        .bio(m.getBio())
                        .status(m.getStatus())
                        .isVerified(m.isVerified())
                        .createdAt(m.getCreatedAt())
                        .updatedAt(m.getUpdatedAt())
                        .build())
                .collect(Collectors.toSet());

        return ConversationResponse.builder()
                .id(conversation.getId())
                .type(conversation.getType().name())
                .name(conversation.getName())
                .members(memberResponses)
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> searchConversations(String query) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        String trimmedQuery = query.trim().toLowerCase();
        if (trimmedQuery.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<Conversation> conversations = conversationRepository.findAllByUserIdOrderByUpdatedAtDesc(currentUser.getId());

        return conversations.stream()
                .filter(c -> {
                    if (c.getType() == ConversationType.GROUP) {
                        return c.getName() != null && c.getName().toLowerCase().contains(trimmedQuery);
                    } else {
                        return c.getMembers().stream()
                                .filter(m -> !m.getId().equals(currentUser.getId()))
                                .anyMatch(m -> m.getUsername().toLowerCase().contains(trimmedQuery));
                    }
                })
                .map(this::mapToConversationResponse)
                .collect(Collectors.toList());
    }
}
