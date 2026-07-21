package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.response.PresenceUpdateResponse;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class PresenceExpiryScheduler {
    private static final long SESSION_TTL_MILLIS = 90_000L;

    private final PresenceService presenceService;
    private final UserRepository userRepository;
    private final SimpMessageSendingOperations messagingTemplate;

    @Scheduled(fixedDelay = 30_000L)
    public void expireStaleSessions() {
        try {
            long cutoff = System.currentTimeMillis() - SESSION_TTL_MILLIS;
            presenceService.expireStaleSessions(cutoff).forEach(userId ->
                    userRepository.findById(userId).ifPresent(user ->
                            messagingTemplate.convertAndSend(
                                    "/topic/presence",
                                    PresenceUpdateResponse.builder()
                                            .userId(user.getId())
                                            .username(user.getUsername())
                                            .status(user.isShowActivityStatus() ? "OFFLINE" : "HIDDEN")
                                            .lastSeen(user.isShowActivityStatus() ? presenceService.getUserLastSeen(user.getId()) : null)
                                            .build()
                            )
                    )
            );
        } catch (Exception e) {
            log.warn("[Redis Presence Cleanup] Redis is currently unavailable: {}", e.getMessage());
        }
    }
}
