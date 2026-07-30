package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.entity.Channel;
import iuh.fit.se.nextalk_be.entity.Group;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.ChannelTaskActivityRepository;
import iuh.fit.se.nextalk_be.repository.ChannelTaskRepository;
import iuh.fit.se.nextalk_be.repository.GroupMemberRepository;
import iuh.fit.se.nextalk_be.repository.GroupRepository;
import iuh.fit.se.nextalk_be.service.FCMService;
import iuh.fit.se.nextalk_be.service.NotificationService;
import iuh.fit.se.nextalk_be.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelTaskActivityServiceSecurityTest {

    @Mock private ChannelTaskActivityRepository activityRepository;
    @Mock private ChannelTaskRepository taskRepository;
    @Mock private ChannelRepository channelRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private UserService userService;
    @Mock private FCMService fcmService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private NotificationService notificationService;

    private ChannelTaskActivityServiceImpl service;
    private User outsider;
    private Group group;
    private Channel channel;

    @BeforeEach
    void setUp() {
        service = new ChannelTaskActivityServiceImpl(
                activityRepository,
                taskRepository,
                channelRepository,
                groupRepository,
                groupMemberRepository,
                userService,
                fcmService,
                messagingTemplate,
                notificationService);

        User owner = User.builder().email("owner@example.test").username("owner").build();
        owner.setId("owner-1");
        outsider = User.builder().email("outsider@example.test").username("outsider").build();
        outsider.setId("outsider-1");
        group = Group.builder().owner(owner).name("Protected group").build();
        group.setId("group-1");
        channel = Channel.builder().group(group).name("Protected channel").build();
        channel.setId("channel-1");

        when(userService.getCurrentAuthenticatedUser()).thenReturn(outsider);
        when(groupRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(channelRepository.findById(channel.getId())).thenReturn(Optional.of(channel));
    }

    @Test
    void outsiderCannotReadOrMarkActivities() {
        when(groupMemberRepository.existsByGroupIdAndUserId(group.getId(), outsider.getId())).thenReturn(false);

        assertThrows(UnauthorizedException.class,
                () -> service.getActivities(group.getId(), channel.getId()));
        assertThrows(UnauthorizedException.class,
                () -> service.markAllAsRead(group.getId(), channel.getId()));

        verify(activityRepository, never())
                .findAllByGroupIdAndChannelIdOrderByCreatedAtDesc(group.getId(), channel.getId());
    }

    @Test
    void channelMustBelongToRequestedGroup() {
        Group anotherGroup = Group.builder().owner(outsider).name("Another").build();
        anotherGroup.setId("group-2");
        channel.setGroup(anotherGroup);

        assertThrows(BadRequestException.class,
                () -> service.getActivities(group.getId(), channel.getId()));
    }
}
