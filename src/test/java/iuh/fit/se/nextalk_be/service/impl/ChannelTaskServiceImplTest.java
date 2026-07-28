package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.entity.Channel;
import iuh.fit.se.nextalk_be.entity.ChannelTask;
import iuh.fit.se.nextalk_be.entity.Group;
import iuh.fit.se.nextalk_be.entity.TaskActivityType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.ChannelTaskRepository;
import iuh.fit.se.nextalk_be.repository.GroupMemberRepository;
import iuh.fit.se.nextalk_be.repository.GroupRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.ChannelTaskActivityService;
import iuh.fit.se.nextalk_be.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelTaskServiceImplTest {

    @Mock private ChannelTaskRepository channelTaskRepository;
    @Mock private ChannelRepository channelRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserService userService;
    @Mock private ChannelTaskActivityService taskActivityService;

    private ChannelTaskServiceImpl service;
    private User owner;
    private Group group;
    private Channel channel;

    @BeforeEach
    void setUp() {
        service = new ChannelTaskServiceImpl(
                channelTaskRepository,
                channelRepository,
                groupRepository,
                groupMemberRepository,
                userRepository,
                messageRepository,
                userService,
                taskActivityService);

        owner = User.builder().username("owner").email("owner@example.com").build();
        owner.setId("user-1");
        group = Group.builder().name("Project A").owner(owner).build();
        group.setId("group-1");
        channel = Channel.builder().name("tasks").group(group).build();
        channel.setId("channel-1");

        when(userService.getCurrentAuthenticatedUser()).thenReturn(owner);
        when(groupRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(channelRepository.findById(channel.getId())).thenReturn(Optional.of(channel));
    }

    @Test
    void getTasksSeparatesActiveAndArchivedRecords() {
        when(channelTaskRepository.findAllByChannelIdAndArchivedNotOrderByCreatedAtDesc(channel.getId(), true))
                .thenReturn(List.of());
        when(channelTaskRepository.findAllByChannelIdAndArchivedTrueOrderByCreatedAtDesc(channel.getId()))
                .thenReturn(List.of());

        service.getTasks(group.getId(), channel.getId(), false);
        service.getTasks(group.getId(), channel.getId(), true);

        verify(channelTaskRepository)
                .findAllByChannelIdAndArchivedNotOrderByCreatedAtDesc(channel.getId(), true);
        verify(channelTaskRepository)
                .findAllByChannelIdAndArchivedTrueOrderByCreatedAtDesc(channel.getId());
    }

    @Test
    void setArchivedPreservesTaskAndSupportsRestore() {
        ChannelTask task = ChannelTask.builder()
                .title("Hoàn thiện đăng nhập")
                .group(group)
                .channel(channel)
                .createdBy(owner)
                .isPinned(true)
                .build();
        task.setId("task-1");

        when(channelTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(channelTaskRepository.save(any(ChannelTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var archived = service.setArchived(group.getId(), channel.getId(), task.getId(), true);

        assertTrue(archived.isArchived());
        assertNotNull(archived.getArchivedAt());
        assertFalse(archived.isPinned());
        assertTrue(task.isArchived());
        verify(taskActivityService).logActivity(
                eq(group.getId()),
                eq(channel.getId()),
                eq(task.getId()),
                eq(owner),
                eq(TaskActivityType.TASK_ARCHIVED),
                any(String.class));

        var restored = service.setArchived(group.getId(), channel.getId(), task.getId(), false);

        assertFalse(restored.isArchived());
        assertNull(restored.getArchivedAt());
        assertFalse(task.isArchived());
        verify(taskActivityService).logActivity(
                eq(group.getId()),
                eq(channel.getId()),
                eq(task.getId()),
                eq(owner),
                eq(TaskActivityType.TASK_RESTORED),
                any(String.class));
    }
}
