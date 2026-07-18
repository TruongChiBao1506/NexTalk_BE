package iuh.fit.se.nextalk_be.security;

import iuh.fit.se.nextalk_be.entity.Channel;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.Group;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.GroupMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketSubscriptionAuthorizerTest {
    private GroupMemberRepository groupMembers;
    private ChannelRepository channels;
    private WebSocketSubscriptionAuthorizer authorizer;
    private User user;

    @BeforeEach
    void setUp() {
        groupMembers = mock(GroupMemberRepository.class);
        channels = mock(ChannelRepository.class);
        authorizer = new WebSocketSubscriptionAuthorizer(groupMembers, channels);
        user = User.builder().username("alice").build();
        user.setId("user-1");
    }

    @Test
    void rejectsUnknownAndForeignGroupTopics() {
        assertThrows(MessageDeliveryException.class, () -> authorizer.authorize(user, "/topic/admin"));
        when(groupMembers.existsByGroupIdAndUserId("group-1", "user-1")).thenReturn(false);
        assertThrows(MessageDeliveryException.class,
                () -> authorizer.authorize(user, "/topic/group.group-1.voice"));
    }

    @Test
    void allowsOwnUserQueueAndMemberGroupTopic() {
        assertDoesNotThrow(() -> authorizer.authorize(user, "/user/queue/private"));
        when(groupMembers.existsByGroupIdAndUserId("group-1", "user-1")).thenReturn(true);
        assertDoesNotThrow(() -> authorizer.authorize(user, "/topic/group.group-1.voice"));
    }

    @Test
    void privateChannelRequiresConversationMembership() {
        Group group = Group.builder().build();
        group.setId("group-1");
        User other = User.builder().build();
        other.setId("user-2");
        Conversation conversation = Conversation.builder().members(Set.of(other)).build();
        Channel channel = Channel.builder().group(group).conversation(conversation).isPrivate(true).build();
        when(channels.findById("channel-1")).thenReturn(Optional.of(channel));
        when(groupMembers.existsByGroupIdAndUserId("group-1", "user-1")).thenReturn(true);

        assertThrows(MessageDeliveryException.class,
                () -> authorizer.authorize(user, "/topic/channel.channel-1.task-activities"));

        conversation.setMembers(Set.of(user));
        assertDoesNotThrow(() -> authorizer.authorize(user, "/topic/channel.channel-1.task-activities"));
    }
}
