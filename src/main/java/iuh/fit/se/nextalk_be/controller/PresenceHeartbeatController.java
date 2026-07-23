package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.response.PresenceUpdateResponse;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.PresenceService;
import iuh.fit.se.nextalk_be.service.VoiceChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class PresenceHeartbeatController {
    private final PresenceService presenceService;
    private final VoiceChannelService voiceChannelService;
    private final UserRepository userRepository;
    private final SimpMessageSendingOperations messagingTemplate;

    private Optional<User> resolveUser(Principal principal) {
        if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof User user) {
            return Optional.of(user);
        }
        if (principal != null) {
            String emailOrUsername = principal.getName();
            return userRepository.findByEmail(emailOrUsername)
                    .or(() -> userRepository.findByUsername(emailOrUsername));
        }
        return Optional.empty();
    }

    @MessageMapping("/presence.heartbeat")
    public void heartbeat(Principal principal, SimpMessageHeaderAccessor headers) {
        String webSocketSessionId = headers.getSessionId();
        if (principal == null || webSocketSessionId == null) return;

        resolveUser(principal).ifPresent(user -> {
            presenceService.touchSession(user.getId(), webSocketSessionId);
            voiceChannelService.touchUser(user.getId());

            String currentStatus = presenceService.getUserStatus(user.getId());
            PresenceUpdateResponse response = PresenceUpdateResponse.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .status(user.isShowActivityStatus() ? currentStatus : "HIDDEN")
                    .lastSeen(user.isShowActivityStatus() ? LocalDateTime.now() : null)
                    .build();

            // Broadcast presence update immediately on heartbeat touch (< 5ms)
            messagingTemplate.convertAndSend("/topic/presence", response);
        });
    }
}
