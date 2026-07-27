package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.response.BirthdayContextResponse;
import iuh.fit.se.nextalk_be.entity.BirthdayVisibility;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.Friendship;
import iuh.fit.se.nextalk_be.entity.FriendshipStatus;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.FriendshipRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationAssistServiceImplTest {

    private ConversationRepository conversationRepository;
    private FriendshipRepository friendshipRepository;
    private UserService userService;
    private ConversationAssistServiceImpl service;
    private User requester;
    private User friend;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        friendshipRepository = mock(FriendshipRepository.class);
        userService = mock(UserService.class);
        service = new ConversationAssistServiceImpl(
                conversationRepository,
                mock(MessageRepository.class),
                friendshipRepository,
                userService,
                mock(StringRedisTemplate.class),
                mock(RestTemplate.class),
                mock(RateLimitService.class)
        );

        requester = User.builder()
                .username("Lan")
                .birthdayReminderEnabled(true)
                .build();
        requester.setId("requester");
        friend = User.builder()
                .username("Minh")
                .birthday(LocalDate.now().toString())
                .birthdayVisibility(BirthdayVisibility.FRIENDS)
                .build();
        friend.setId("friend");

        conversation = Conversation.builder()
                .type(ConversationType.PRIVATE)
                .members(new HashSet<>(java.util.List.of(requester, friend)))
                .build();
        conversation.setId("conversation");
        when(userService.getCurrentAuthenticatedUser()).thenReturn(requester);
        when(conversationRepository.findById("conversation")).thenReturn(Optional.of(conversation));
    }

    @Test
    void birthdayContextReturnsTemplatesForAcceptedFriend() {
        Friendship accepted = Friendship.builder().status(FriendshipStatus.ACCEPTED).build();
        when(friendshipRepository.findFriendshipBetweenUsers("requester", "friend"))
                .thenReturn(Optional.of(accepted));

        BirthdayContextResponse response = service.getBirthdayContext("conversation");

        assertTrue(response.isHasBirthday());
        assertEquals(0, response.getDaysUntil());
        assertEquals(3, response.getTemplates().size());
        assertTrue(response.getMessage().contains("Minh"));
    }

    @Test
    void birthdayContextHidesBirthdayWhenVisibilityIsNone() {
        friend.setBirthdayVisibility(BirthdayVisibility.NONE);
        Friendship accepted = Friendship.builder().status(FriendshipStatus.ACCEPTED).build();
        when(friendshipRepository.findFriendshipBetweenUsers("requester", "friend"))
                .thenReturn(Optional.of(accepted));

        BirthdayContextResponse response = service.getBirthdayContext("conversation");

        assertFalse(response.isHasBirthday());
        assertTrue(response.getTemplates().isEmpty());
    }
}
