package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.entity.Channel;
import iuh.fit.se.nextalk_be.entity.ChannelTask;
import iuh.fit.se.nextalk_be.entity.ChannelTaskStatus;
import iuh.fit.se.nextalk_be.entity.Group;
import iuh.fit.se.nextalk_be.entity.TaskActivityType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.ChannelTaskActivityRepository;
import iuh.fit.se.nextalk_be.repository.ChannelTaskRepository;
import iuh.fit.se.nextalk_be.repository.GroupMemberRepository;
import iuh.fit.se.nextalk_be.repository.GroupRepository;
import iuh.fit.se.nextalk_be.service.NotificationService;
import iuh.fit.se.nextalk_be.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
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

        lenient().when(userService.getCurrentAuthenticatedUser()).thenReturn(outsider);
        lenient().when(groupRepository.findById(group.getId())).thenReturn(Optional.of(group));
        lenient().when(channelRepository.findById(channel.getId())).thenReturn(Optional.of(channel));
    }

    @Test
    void outsiderCannotReadOrMarkActivities() {
        when(groupMemberRepository.existsByGroupIdAndUserId(group.getId(), outsider.getId())).thenReturn(false);

        assertThrows(UnauthorizedException.class,
                () -> service.getActivities(group.getId(), channel.getId()));
        assertThrows(UnauthorizedException.class,
                () -> service.markAllAsRead(group.getId(), channel.getId()));

        verify(activityRepository, never())
                .findTop200ByGroupIdAndChannelIdOrderByCreatedAtDesc(group.getId(), channel.getId());
        verify(activityRepository, never())
                .markAllAsRead(group.getId(), channel.getId(), outsider.getId());
    }

    @Test
    void channelMustBelongToRequestedGroup() {
        Group anotherGroup = Group.builder().owner(outsider).name("Another").build();
        anotherGroup.setId("group-2");
        channel.setGroup(anotherGroup);

        assertThrows(BadRequestException.class,
                () -> service.getActivities(group.getId(), channel.getId()));
    }

    @Test
    void marksActivitiesReadWithOneServerSideUpdate() {
        group.setOwner(outsider);

        service.markAllAsRead(group.getId(), channel.getId());

        verify(activityRepository).markAllAsRead(group.getId(), channel.getId(), outsider.getId());
        verify(activityRepository, never()).saveAll(any());
    }

    @Test
    void deadlineSchedulerUsesBoundedIndexedCandidatesInsteadOfFullScan() {
        ChannelTask task = ChannelTask.builder()
                .title("Internal task")
                .status(ChannelTaskStatus.TODO)
                .group(group)
                .channel(channel)
                .dueAt(LocalDateTime.now().minusMinutes(1))
                .build();
        task.setId("task-1");
        when(taskRepository.findDeadlineCandidates(
                any(),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(List.of(task));
        when(activityRepository.existsByTaskIdAndType(task.getId(), TaskActivityType.TASK_OVERDUE))
                .thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);

        service.scanAndRemindTasks();

        verify(taskRepository, never()).findAll();
        verify(taskRepository).findDeadlineCandidates(
                any(),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.argThat(pageable ->
                        pageable.getPageNumber() == 0 && pageable.getPageSize() == 100)
        );
        assertTrue(task.isOverdueNotificationSent());
    }
}
