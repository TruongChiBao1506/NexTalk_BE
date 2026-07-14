package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class PresenceHeartbeatController {
    private final PresenceService presenceService;
    private final UserRepository userRepository;

    @MessageMapping("/presence.heartbeat")
    public void heartbeat(Principal principal, SimpMessageHeaderAccessor headers) {
        String webSocketSessionId = headers.getSessionId();
        if (principal == null || webSocketSessionId == null) return;
        userRepository.findByEmail(principal.getName())
                .or(() -> userRepository.findByUsername(principal.getName()))
                .ifPresent(user -> presenceService.touchSession(user.getId(), webSocketSessionId));
    }
}
