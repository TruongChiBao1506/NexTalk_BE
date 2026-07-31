package iuh.fit.se.nextalk_be.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.nextalk_be.dto.request.CreateGroupEventRequest;
import iuh.fit.se.nextalk_be.dto.request.UpdateGroupEventRsvpRequest;
import iuh.fit.se.nextalk_be.entity.*;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.*;
import iuh.fit.se.nextalk_be.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupEventServiceSecurityTest {
    @Mock GroupEventRepository eventRepository;
    @Mock GroupRepository groupRepository;
    @Mock GroupMemberRepository memberRepository;
    @Mock ChannelRepository channelRepository;
    @Mock UserRepository userRepository;
    @Mock UserService userService;
    @Mock MessageService messageService;
    @Mock NotificationService notificationService;
    @Mock ObjectMapper objectMapper;
    @InjectMocks GroupEventService service;

    @Test
    void upcomingEventsRejectsNonMember() {
        User user = user("u1");
        Group group = group("g1");
        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);
        when(groupRepository.findById("g1")).thenReturn(Optional.of(group));
        when(memberRepository.existsByGroupIdAndUserId("g1", "u1")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.getUpcoming("g1"));
    }

    @Test
    void regularMemberCannotCreateWhenSettingIsDisabled() {
        User user = user("u1");
        Group group = group("g1");
        group.setMembersCanCreateEvents(false);
        GroupMember membership = GroupMember.builder().group(group).user(user).role(GroupRole.MEMBER).build();
        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);
        when(groupRepository.findById("g1")).thenReturn(Optional.of(group));
        when(memberRepository.existsByGroupIdAndUserId("g1", "u1")).thenReturn(true);
        when(memberRepository.findByGroupIdAndUserId("g1", "u1")).thenReturn(Optional.of(membership));

        CreateGroupEventRequest request = new CreateGroupEventRequest(
                "conversation-1", "Họp nhóm", null, null, null,
                LocalDateTime.now().plusDays(1), null, 60);
        assertThrows(AccessDeniedException.class, () -> service.create("g1", request));
    }

    @Test
    void rsvpDoesNotRevealEventFromAnotherGroup() {
        User user = user("u1");
        Group requestedGroup = group("g1");
        Group otherGroup = group("g2");
        GroupEvent event = GroupEvent.builder().group(otherGroup).creator(user).build();
        event.setId("event-1");
        when(userService.getCurrentAuthenticatedUser()).thenReturn(user);
        when(groupRepository.findById("g1")).thenReturn(Optional.of(requestedGroup));
        when(memberRepository.existsByGroupIdAndUserId("g1", "u1")).thenReturn(true);
        when(eventRepository.findById("event-1")).thenReturn(Optional.of(event));

        assertThrows(ResourceNotFoundException.class, () -> service.rsvp(
                "g1", "event-1", new UpdateGroupEventRsvpRequest(GroupEventRsvpStatus.ATTENDING)));
    }

    private User user(String id) {
        User user = User.builder().username("tester").email("tester@example.invalid").build();
        user.setId(id);
        return user;
    }

    private Group group(String id) {
        Group group = Group.builder().name("Group").build();
        group.setId(id);
        return group;
    }
}
