package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.entity.FriendSuggestionDismissal;
import iuh.fit.se.nextalk_be.entity.Friendship;
import iuh.fit.se.nextalk_be.entity.FriendshipStatus;
import iuh.fit.se.nextalk_be.entity.Group;
import iuh.fit.se.nextalk_be.entity.GroupMember;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.entity.UserBlock;
import iuh.fit.se.nextalk_be.repository.ChatRequestRepository;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.FriendSuggestionDismissalRepository;
import iuh.fit.se.nextalk_be.repository.FriendshipRepository;
import iuh.fit.se.nextalk_be.repository.GroupMemberRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.UserBlockRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.NotificationService;
import iuh.fit.se.nextalk_be.service.PresenceService;
import iuh.fit.se.nextalk_be.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendServiceImplSuggestionTest {

    @Mock private FriendshipRepository friendshipRepository;
    @Mock private UserService userService;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private PresenceService presenceService;
    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ChatRequestRepository chatRequestRepository;
    @Mock private UserBlockRepository userBlockRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private FriendSuggestionDismissalRepository dismissalRepository;

    private FriendServiceImpl service;
    private User currentUser;

    @BeforeEach
    void setUp() {
        service = new FriendServiceImpl(
                friendshipRepository,
                userService,
                userRepository,
                notificationService,
                presenceService,
                conversationRepository,
                messageRepository,
                messagingTemplate,
                chatRequestRepository,
                userBlockRepository,
                groupMemberRepository,
                dismissalRepository);

        currentUser = user("current", "current-user");
        when(userService.getCurrentAuthenticatedUser()).thenReturn(currentUser);
    }

    @Test
    void coldStartUsesSharedGroupAndReturnsAnExplainableSuggestion() {
        User candidate = user("candidate", "candidate-user");
        Group group = Group.builder().name("Developers").build();
        group.setId("group-1");

        stubEmptyRelationshipState();
        when(groupMemberRepository.findAllByUserId(currentUser.getId()))
                .thenReturn(List.of(GroupMember.builder().group(group).user(currentUser).build()));
        when(groupMemberRepository.findFriendSuggestionCandidatesByGroupIdIn(
                eq(List.of(group.getId())),
                eq(List.of()),
                any(Pageable.class)))
                .thenReturn(List.of(
                        GroupMember.builder().group(group).user(currentUser).build(),
                        GroupMember.builder().group(group).user(candidate).build()));

        var suggestions = service.getFriendSuggestions(10);

        assertEquals(1, suggestions.size());
        assertEquals(candidate.getId(), suggestions.get(0).getId());
        assertEquals(1, suggestions.get(0).getSharedGroupsCount());
        assertEquals("1 nhóm chung", suggestions.get(0).getSuggestionReason());
        assertFalse(suggestions.get(0).isRequestSent());
    }

    @Test
    void coldStartFallsBackToDiscoverableAccountsWithoutExposingEmail() {
        User candidate = user("new-member", "candidate-user");
        candidate.setVerified(true);

        stubEmptyRelationshipState();
        when(groupMemberRepository.findAllByUserId(currentUser.getId())).thenReturn(List.of());
        when(userRepository.findFriendSuggestionDiscoveryCandidates(any(Pageable.class)))
                .thenReturn(List.of(currentUser, candidate));

        var suggestions = service.getFriendSuggestions(10);

        assertEquals(1, suggestions.size());
        assertEquals(candidate.getId(), suggestions.get(0).getId());
        assertEquals("Thành viên đã xác minh trên NexTalk", suggestions.get(0).getSuggestionReason());
    }

    @Test
    void rankingLoadsFriendNetworkOnceInsteadOfQueryingEveryFriend() {
        User friend = user("friend", "friend-user");
        User candidate = user("candidate", "candidate-user");
        Friendship myFriendship = friendship(currentUser, friend);
        Friendship friendOfFriend = friendship(friend, candidate);

        when(friendshipRepository.findAllByUserIdAndStatus(currentUser.getId(), FriendshipStatus.ACCEPTED))
                .thenReturn(List.of(myFriendship));
        when(friendshipRepository.findBySenderIdAndStatus(currentUser.getId(), FriendshipStatus.PENDING))
                .thenReturn(List.of());
        when(friendshipRepository.findByReceiverIdAndStatus(currentUser.getId(), FriendshipStatus.PENDING))
                .thenReturn(List.of());
        when(userBlockRepository.findAllByBlockerIdOrBlockedId(currentUser.getId(), currentUser.getId()))
                .thenReturn(List.of());
        when(dismissalRepository.findAllByUserIdAndExpiresAtAfter(
                eq(currentUser.getId()),
                any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(friendshipRepository.findBySenderIdInAndStatusOrReceiverIdInAndStatus(
                eq(java.util.Set.of(friend.getId())),
                eq(FriendshipStatus.ACCEPTED),
                eq(java.util.Set.of(friend.getId())),
                eq(FriendshipStatus.ACCEPTED),
                any(Pageable.class)))
                .thenReturn(List.of(friendOfFriend));
        when(groupMemberRepository.findAllByUserId(currentUser.getId())).thenReturn(List.of());

        var suggestions = service.getFriendSuggestions(1);

        assertEquals(candidate.getId(), suggestions.get(0).getId());
        assertEquals(1, suggestions.get(0).getMutualFriendsCount());
        verify(friendshipRepository, times(1))
                .findAllByUserIdAndStatus(currentUser.getId(), FriendshipStatus.ACCEPTED);
        verify(friendshipRepository, times(1))
                .findBySenderIdInAndStatusOrReceiverIdInAndStatus(
                        eq(java.util.Set.of(friend.getId())),
                        eq(FriendshipStatus.ACCEPTED),
                        eq(java.util.Set.of(friend.getId())),
                        eq(FriendshipStatus.ACCEPTED),
                        any(Pageable.class));
    }

    @Test
    void blockedAndDismissedUsersAreExcludedFromDiscovery() {
        User blocked = user("blocked", "blocked-user");
        User dismissed = user("dismissed", "dismissed-user");
        User optedOut = user("opted-out", "opted-out-user");
        optedOut.setFriendSuggestionDiscoverable(false);
        User visible = user("visible", "visible-user");

        when(friendshipRepository.findAllByUserIdAndStatus(currentUser.getId(), FriendshipStatus.ACCEPTED))
                .thenReturn(List.of());
        when(friendshipRepository.findBySenderIdAndStatus(currentUser.getId(), FriendshipStatus.PENDING))
                .thenReturn(List.of());
        when(friendshipRepository.findByReceiverIdAndStatus(currentUser.getId(), FriendshipStatus.PENDING))
                .thenReturn(List.of());
        when(userBlockRepository.findAllByBlockerIdOrBlockedId(currentUser.getId(), currentUser.getId()))
                .thenReturn(List.of(UserBlock.builder().blocker(currentUser).blocked(blocked).build()));
        when(dismissalRepository.findAllByUserIdAndExpiresAtAfter(
                eq(currentUser.getId()),
                any(LocalDateTime.class)))
                .thenReturn(List.of(FriendSuggestionDismissal.builder()
                        .userId(currentUser.getId())
                        .candidateUserId(dismissed.getId())
                        .expiresAt(LocalDateTime.now().plusDays(1))
                        .build()));
        when(groupMemberRepository.findAllByUserId(currentUser.getId())).thenReturn(List.of());
        when(userRepository.findFriendSuggestionDiscoveryCandidates(any(Pageable.class)))
                .thenReturn(List.of(blocked, dismissed, optedOut, visible));

        var suggestions = service.getFriendSuggestions(10);

        assertEquals(List.of(visible.getId()), suggestions.stream().map(item -> item.getId()).toList());
    }

    @Test
    void dismissPersistsOnlyForTheAuthenticatedUserForThirtyDays() {
        User candidate = user("candidate", "candidate-user");
        when(userRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(dismissalRepository.findByUserIdAndCandidateUserId(currentUser.getId(), candidate.getId()))
                .thenReturn(Optional.empty());

        LocalDateTime before = LocalDateTime.now().plusDays(29);
        service.dismissFriendSuggestion(candidate.getId());

        ArgumentCaptor<FriendSuggestionDismissal> captor =
                ArgumentCaptor.forClass(FriendSuggestionDismissal.class);
        verify(dismissalRepository).save(captor.capture());
        assertEquals(currentUser.getId(), captor.getValue().getUserId());
        assertEquals(candidate.getId(), captor.getValue().getCandidateUserId());
        assertTrue(captor.getValue().getExpiresAt().isAfter(before));
    }

    private void stubEmptyRelationshipState() {
        when(friendshipRepository.findAllByUserIdAndStatus(currentUser.getId(), FriendshipStatus.ACCEPTED))
                .thenReturn(List.of());
        when(friendshipRepository.findBySenderIdAndStatus(currentUser.getId(), FriendshipStatus.PENDING))
                .thenReturn(List.of());
        when(friendshipRepository.findByReceiverIdAndStatus(currentUser.getId(), FriendshipStatus.PENDING))
                .thenReturn(List.of());
        when(userBlockRepository.findAllByBlockerIdOrBlockedId(currentUser.getId(), currentUser.getId()))
                .thenReturn(List.of());
        when(dismissalRepository.findAllByUserIdAndExpiresAtAfter(
                eq(currentUser.getId()),
                any(LocalDateTime.class)))
                .thenReturn(List.of());
    }

    private User user(String username, String id) {
        User user = User.builder()
                .username(username)
                .email(username + "@example.test")
                .build();
        user.setId(id);
        return user;
    }

    private Friendship friendship(User sender, User receiver) {
        return Friendship.builder()
                .sender(sender)
                .receiver(receiver)
                .status(FriendshipStatus.ACCEPTED)
                .build();
    }
}
