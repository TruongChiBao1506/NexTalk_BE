package iuh.fit.se.nextalk_be.event;

import iuh.fit.se.nextalk_be.dto.response.PresenceUpdateResponse;
import iuh.fit.se.nextalk_be.entity.Channel;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.event.VoiceChannelEvent;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.PresenceService;
import iuh.fit.se.nextalk_be.service.VoiceChannelService;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceEventListener {

    private final PresenceService presenceService;
    private final SimpMessageSendingOperations messagingTemplate;
    private final UserRepository userRepository;
    private final VoiceChannelService voiceChannelService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();
        String sessionId = headerAccessor.getSessionId();

        if (principal != null && sessionId != null) {
            String emailOrUsername = principal.getName();
            userRepository.findByEmail(emailOrUsername)
                    .or(() -> userRepository.findByUsername(emailOrUsername))
                    .ifPresent(user -> {
                        presenceService.addSession(user.getId(), sessionId);
                        String currentStatus = presenceService.getUserStatus(user.getId());
                        log.info("User {} connected with session {}. Status is {}", user.getUsername(), sessionId, currentStatus);

                        // Broadcast presence update
                        PresenceUpdateResponse response = PresenceUpdateResponse.builder()
                                .userId(user.getId())
                                .username(user.getUsername())
                                .status(currentStatus)
                                .lastSeen(LocalDateTime.now())
                                .build();
                        messagingTemplate.convertAndSend("/topic/presence", response);
                    });
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();
        String sessionId = headerAccessor.getSessionId();

        if (principal != null && sessionId != null) {
            String emailOrUsername = principal.getName();
            userRepository.findByEmail(emailOrUsername)
                    .or(() -> userRepository.findByUsername(emailOrUsername))
                    .ifPresent(user -> {
                        presenceService.removeSession(user.getId(), sessionId);
                        String currentStatus = presenceService.getUserStatus(user.getId());
                        log.info("User {} disconnected with session {}. New status is {}", user.getUsername(), sessionId, currentStatus);

                        // Voice Channel cleanup
                        String[] channelInfo = voiceChannelService.leaveCurrentChannel(user.getId());
                        if (channelInfo != null) {
                            String channelId = channelInfo[0];
                            String groupId = channelInfo[1];
                            
                            VoiceChannelEvent leaveEvent = 
                                VoiceChannelEvent.builder()
                                    .type("LEAVE")
                                    .channelId(channelId)
                                    .groupId(groupId)
                                    .userId(user.getId())
                                    .currentMembers(voiceChannelService.getChannelMembers(channelId))
                                    .build();
                            
                            messagingTemplate.convertAndSend("/topic/group." + groupId + ".voice", leaveEvent);
                        }

                        LocalDateTime lastSeen = presenceService.getUserLastSeen(user.getId());
                        PresenceUpdateResponse response = PresenceUpdateResponse.builder()
                                .userId(user.getId())
                                .username(user.getUsername())
                                .status(currentStatus)
                                .lastSeen(lastSeen != null ? lastSeen : LocalDateTime.now())
                                .build();
                        messagingTemplate.convertAndSend("/topic/presence", response);
                    });
        }
    }
}
