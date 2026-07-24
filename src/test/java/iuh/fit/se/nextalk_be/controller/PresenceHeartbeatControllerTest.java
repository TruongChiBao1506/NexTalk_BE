package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.PresenceService;
import iuh.fit.se.nextalk_be.service.VoiceChannelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresenceHeartbeatControllerTest {

    private PresenceService presenceService;
    private VoiceChannelService voiceChannelService;
    private UserRepository userRepository;
    private PresenceHeartbeatController controller;

    @BeforeEach
    void setUp() {
        presenceService = mock(PresenceService.class);
        voiceChannelService = mock(VoiceChannelService.class);
        userRepository = mock(UserRepository.class);
        controller = new PresenceHeartbeatController(presenceService, voiceChannelService, userRepository);
    }

    @Test
    void heartbeatOnlyRefreshesSessionLeases() {
        User user = User.builder().username("alice").build();
        user.setId("user-1");
        UsernamePasswordAuthenticationToken principal =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);
        when(headers.getSessionId()).thenReturn("socket-1");

        controller.heartbeat(principal, headers);

        verify(presenceService).touchSession("user-1", "socket-1");
        verify(voiceChannelService).touchUser("user-1");
        verify(presenceService, never()).getUserStatus("user-1");
    }

    @Test
    void heartbeatWithoutSessionDoesNothing() {
        SimpMessageHeaderAccessor headers = mock(SimpMessageHeaderAccessor.class);

        controller.heartbeat(() -> "alice", headers);

        verify(userRepository, never()).findByEmail("alice");
        verify(userRepository, never()).findByUsername("alice");
    }
}
