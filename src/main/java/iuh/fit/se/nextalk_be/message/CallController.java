package iuh.fit.se.nextalk_be.message;

import iuh.fit.se.nextalk_be.common.ApiResponse;
import iuh.fit.se.nextalk_be.conversation.Conversation;
import iuh.fit.se.nextalk_be.conversation.ConversationRepository;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.message.dto.CallSignal;
import iuh.fit.se.nextalk_be.message.dto.CallTokenResponse;
import iuh.fit.se.nextalk_be.user.User;
import iuh.fit.se.nextalk_be.user.UserRepository;
import iuh.fit.se.nextalk_be.user.UserService;
import io.agora.media.RtcTokenBuilder2;
import io.agora.media.RtcTokenBuilder2.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallController {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.agora.app-id}")
    private String appId;

    @Value("${app.agora.app-certificate}")
    private String appCertificate;

    @GetMapping("/token")
    public ResponseEntity<ApiResponse<CallTokenResponse>> getCallToken(
            @RequestParam("conversationId") String conversationId
    ) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        boolean isMember = conversation.getMembers().stream()
                .anyMatch(m -> m.getId().equals(currentUser.getId()));

        if (!isMember) {
            throw new BadRequestException("You are not a member of this conversation");
        }

        int uid = currentUser.getId().hashCode() & 0x7FFFFFFF;
        String channelName = conversationId;

        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();
        // Expiration times (in seconds): 2 hours = 7200s
        int tokenExpire = 7200;
        int privilegeExpire = 7200;

        String token = tokenBuilder.buildTokenWithUid(
                appId,
                appCertificate,
                channelName,
                uid,
                Role.ROLE_PUBLISHER,
                tokenExpire,
                privilegeExpire
        );

        CallTokenResponse response = CallTokenResponse.builder()
                .token(token)
                .uid(uid)
                .channelName(channelName)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Agora token generated successfully"));
    }

    @MessageMapping("/call.invite")
    public void inviteCall(@Payload CallSignal signal, Principal principal) {
        if (principal == null) return;
        User caller = findUserByPrincipal(principal);
        if (caller == null) return;

        signal.setCallerId(caller.getId());
        signal.setCallerName(caller.getUsername());
        signal.setCallerAvatar(caller.getAvatarUrl());
        signal.setSignalType("INVITE");

        forwardCallSignal(signal, caller.getId());
    }

    @MessageMapping("/call.answer")
    public void answerCall(@Payload CallSignal signal, Principal principal) {
        if (principal == null) return;
        User responder = findUserByPrincipal(principal);
        if (responder == null) return;

        // Route the reply back to the caller
        signal.setReceiverId(signal.getCallerId());
        String callerUsername = userRepository.findById(signal.getCallerId())
                .map(User::getUsername)
                .orElse(null);

        if (callerUsername != null) {
            messagingTemplate.convertAndSendToUser(
                    callerUsername,
                    "/queue/calls",
                    signal
            );
        }
    }

    @MessageMapping("/call.cancel")
    public void cancelCall(@Payload CallSignal signal, Principal principal) {
        if (principal == null) return;
        forwardToTarget(signal);
    }

    @MessageMapping("/call.hangup")
    public void hangupCall(@Payload CallSignal signal, Principal principal) {
        if (principal == null) return;
        forwardToTarget(signal);
    }

    private User findUserByPrincipal(Principal principal) {
        String name = principal.getName();
        return userRepository.findByEmail(name)
                .or(() -> userRepository.findByUsername(name))
                .orElse(null);
    }

    private void forwardToTarget(CallSignal signal) {
        if (signal.getReceiverId() != null) {
            String receiverUsername = userRepository.findById(signal.getReceiverId())
                    .map(User::getUsername)
                    .orElse(null);
            if (receiverUsername != null) {
                messagingTemplate.convertAndSendToUser(
                        receiverUsername,
                        "/queue/calls",
                        signal
                );
            }
        } else {
            forwardCallSignal(signal, signal.getCallerId());
        }
    }

    private void forwardCallSignal(CallSignal signal, String senderId) {
        Conversation conversation = conversationRepository.findById(signal.getConversationId()).orElse(null);
        if (conversation == null) return;

        for (User member : conversation.getMembers()) {
            if (!member.getId().equals(senderId)) {
                if (signal.getReceiverId() != null && !member.getId().equals(signal.getReceiverId())) {
                    continue;
                }
                messagingTemplate.convertAndSendToUser(
                        member.getUsername(),
                        "/queue/calls",
                        signal
                );
            }
        }
    }
}
