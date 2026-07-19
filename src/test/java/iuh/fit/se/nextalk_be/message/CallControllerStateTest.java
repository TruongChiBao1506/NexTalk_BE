package iuh.fit.se.nextalk_be.message;

import iuh.fit.se.nextalk_be.controller.CallController;
import iuh.fit.se.nextalk_be.dto.CallSignal;
import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.entity.Channel;
import iuh.fit.se.nextalk_be.entity.ChannelType;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.Group;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.GroupMemberRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.FCMService;
import iuh.fit.se.nextalk_be.service.MessageService;
import iuh.fit.se.nextalk_be.service.UserService;
import iuh.fit.se.nextalk_be.service.VoiceChannelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallControllerStateTest {

    @Mock private UserRepository userRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private ChannelRepository channelRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private UserService userService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private MessageService messageService;
    @Mock private VoiceChannelService voiceChannelService;
    @Mock private FCMService fcmService;
    @Mock private StringRedisTemplate redisTemplate;

    private CallController controller;
    private User caller;
    private User firstResponder;
    private User secondResponder;

    @BeforeEach
    void setUp() {
        controller = new CallController(
                userRepository,
                conversationRepository,
                channelRepository,
                groupMemberRepository,
                userService,
                messagingTemplate,
                messageService,
                voiceChannelService,
                fcmService,
                redisTemplate
        );
        ReflectionTestUtils.setField(controller, "ringingCallTtl", Duration.ofSeconds(75));
        ReflectionTestUtils.setField(controller, "connectedCallTtl", Duration.ofHours(2));

        caller = user("caller", "caller@example.com");
        firstResponder = user("first", "first@example.com");
        secondResponder = user("second", "second@example.com");
        lenient().when(userRepository.findByEmail(any())).thenAnswer(invocation -> {
            String email = invocation.getArgument(0);
            return Optional.ofNullable(switch (email) {
                case "caller@example.com" -> caller;
                case "first@example.com" -> firstResponder;
                case "second@example.com" -> secondResponder;
                default -> null;
            });
        });
    }

    @Test
    void groupCallRemainsAcceptableAfterAnotherMemberRejects() {
        Conversation conversation = conversation("group-conversation", ConversationType.GROUP,
                caller, firstResponder, secondResponder);
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));

        controller.inviteCall(invite("group-call", conversation.getId(), null), principal(caller));
        controller.answerCall(answer("group-call", conversation.getId(), false), principal(firstResponder));
        controller.answerCall(answer("group-call", conversation.getId(), true), principal(secondResponder));

        ArgumentCaptor<CallSignal> responses = ArgumentCaptor.forClass(CallSignal.class);
        verify(messagingTemplate, org.mockito.Mockito.times(2))
                .convertAndSendToUser(eq(caller.getUsername()), eq("/queue/calls"), responses.capture());
        assertTrue(Boolean.TRUE.equals(responses.getAllValues().get(1).getAccept()));
    }

    @Test
    void onlyTheDeviceThatClaimedTheCallCanRepeatAnAccept() {
        Conversation conversation = conversation("private-conversation", ConversationType.PRIVATE,
                caller, firstResponder);
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));

        controller.inviteCall(invite("private-call", conversation.getId(), firstResponder.getId()), principal(caller));
        CallSignal response = answer("private-call", conversation.getId(), true);

        ResponseEntity<ApiResponse<Void>> first = controller.respondToCallRest(
                response, principal(firstResponder), "fcm-a", "device-a");
        ResponseEntity<ApiResponse<Void>> sameDeviceRetry = controller.respondToCallRest(
                response, principal(firstResponder), "fcm-a", "device-a");
        ResponseEntity<ApiResponse<Void>> otherDevice = controller.respondToCallRest(
                response, principal(firstResponder), "fcm-b", "device-b");

        assertEquals(200, first.getStatusCode().value());
        assertEquals(200, sameDeviceRetry.getStatusCode().value());
        assertEquals(409, otherDevice.getStatusCode().value());

        ArgumentCaptor<CallSignal> responderSignals = ArgumentCaptor.forClass(CallSignal.class);
        verify(messagingTemplate, org.mockito.Mockito.times(2))
                .convertAndSendToUser(eq(firstResponder.getUsername()), eq("/queue/calls"), responderSignals.capture());
        CallSignal handledSignal = responderSignals.getAllValues().get(1);
        assertEquals("CALL_HANDLED", handledSignal.getSignalType());
        assertEquals("device-a", handledSignal.getHandledByDeviceId());
    }

    @Test
    void nonMemberCannotStartConversationCall() {
        Conversation conversation = conversation("private-conversation", ConversationType.PRIVATE,
                firstResponder, secondResponder);
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        controller.inviteCall(invite("unauthorized-call", conversation.getId(), firstResponder.getId()), principal(caller));

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any(Object.class));
    }

    @Test
    void nonMemberCannotObtainVoiceChannelToken() {
        Group group = Group.builder().name("group").owner(firstResponder).build();
        group.setId("group-id");
        Channel channel = Channel.builder()
                .name("voice")
                .type(ChannelType.VOICE)
                .group(group)
                .build();
        channel.setId("voice-id");
        when(userService.getCurrentAuthenticatedUser()).thenReturn(caller);
        when(channelRepository.findById(channel.getId())).thenReturn(Optional.of(channel));
        when(groupMemberRepository.existsByGroupIdAndUserId(group.getId(), caller.getId())).thenReturn(false);

        assertThrows(BadRequestException.class,
                () -> controller.getChannelToken(channel.getId(), group.getId()));
    }

    private User user(String id, String email) {
        User user = User.builder().email(email).username(id).build();
        user.setId(id);
        return user;
    }

    private Conversation conversation(String id, ConversationType type, User... members) {
        Conversation conversation = Conversation.builder()
                .type(type)
                .members(Set.of(members))
                .build();
        conversation.setId(id);
        return conversation;
    }

    private CallSignal invite(String callId, String conversationId, String receiverId) {
        return CallSignal.builder()
                .callId(callId)
                .conversationId(conversationId)
                .receiverId(receiverId)
                .type("VOICE")
                .signalType("INVITE")
                .build();
    }

    private CallSignal answer(String callId, String conversationId, boolean accept) {
        return CallSignal.builder()
                .callId(callId)
                .conversationId(conversationId)
                .callerId(caller.getId())
                .type("VOICE")
                .signalType("ANSWER")
                .accept(accept)
                .reason(accept ? "accepted" : "rejected")
                .build();
    }

    private Principal principal(User user) {
        return () -> user.getEmail();
    }
}
