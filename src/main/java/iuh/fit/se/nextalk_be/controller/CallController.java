package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.CallSignal;
import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.CallTokenResponse;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.event.VoiceChannelEvent;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.MessageService;
import iuh.fit.se.nextalk_be.service.UserService;
import iuh.fit.se.nextalk_be.service.VoiceChannelService;
import iuh.fit.se.nextalk_be.service.FCMService;


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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallController {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;
    private final VoiceChannelService voiceChannelService;
    private final FCMService fcmService;
    private final Map<String, CallSession> activeCallSessions = new ConcurrentHashMap<>();

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
        String resolvedAppId = resolveAgoraConfig("AGORA_APP_ID", appId);
        String resolvedAppCertificate = resolveAgoraConfig("AGORA_APP_CERTIFICATE", appCertificate);
        if (resolvedAppId.isBlank() || resolvedAppCertificate.isBlank()) {
            throw new BadRequestException("Agora is not configured");
        }

        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();
        // Expiration times (in seconds): 2 hours = 7200s
        int tokenExpire = 7200;
        int privilegeExpire = 7200;

        String token = tokenBuilder.buildTokenWithUid(
                resolvedAppId,
                resolvedAppCertificate,
                channelName,
                uid,
                Role.ROLE_PUBLISHER,
                tokenExpire,
                privilegeExpire
        );

        CallTokenResponse response = CallTokenResponse.builder()
                .appId(resolvedAppId)
                .token(token)
                .uid(uid)
                .channelName(channelName)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Agora token generated successfully"));
    }

    @GetMapping("/channel-token")
    public ResponseEntity<ApiResponse<CallTokenResponse>> getChannelToken(
            @RequestParam("channelId") String channelId,
            @RequestParam("groupId") String groupId
    ) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        
        int uid = currentUser.getId().hashCode() & 0x7FFFFFFF;
        String channelName = channelId;
        String resolvedAppId = resolveAgoraConfig("AGORA_APP_ID", appId);
        String resolvedAppCertificate = resolveAgoraConfig("AGORA_APP_CERTIFICATE", appCertificate);
        if (resolvedAppId.isBlank() || resolvedAppCertificate.isBlank()) {
            throw new BadRequestException("Agora is not configured");
        }

        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();
        int tokenExpire = 7200;
        int privilegeExpire = 7200;

        String token = tokenBuilder.buildTokenWithUid(
                resolvedAppId, resolvedAppCertificate, channelName, uid, Role.ROLE_PUBLISHER, tokenExpire, privilegeExpire
        );

        CallTokenResponse response = CallTokenResponse.builder()
                .appId(resolvedAppId)
                .token(token)
                .uid(uid)
                .channelName(channelName)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Agora channel token generated successfully"));
    }

    @MessageMapping("/voice.join")
    public void joinVoiceChannel(@Payload VoiceChannelEvent event, Principal principal) {
        if (principal == null) return;
        User user = findUserByPrincipal(principal);
        if (user == null) return;

        voiceChannelService.joinChannel(event.getChannelId(), user.getId(), event.getGroupId());
        
        event.setType("JOIN");
        event.setUserId(user.getId());
        event.setCurrentMembers(voiceChannelService.getChannelMembers(event.getChannelId()));
        
        messagingTemplate.convertAndSend("/topic/group." + event.getGroupId() + ".voice", event);
    }

    @MessageMapping("/voice.leave")
    public void leaveVoiceChannel(@Payload VoiceChannelEvent event, Principal principal) {
        if (principal == null) return;
        User user = findUserByPrincipal(principal);
        if (user == null) return;

        String[] channelInfo = voiceChannelService.leaveCurrentChannel(user.getId());
        if (channelInfo != null) {
            String channelId = channelInfo[0];
            String groupId = channelInfo[1];
            
            VoiceChannelEvent leaveEvent = VoiceChannelEvent.builder()
                    .type("LEAVE")
                    .channelId(channelId)
                    .groupId(groupId)
                    .userId(user.getId())
                    .currentMembers(voiceChannelService.getChannelMembers(channelId))
                    .build();
            
            messagingTemplate.convertAndSend("/topic/group." + groupId + ".voice", leaveEvent);
        }
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

        registerCallInvite(signal, caller);
        forwardCallSignal(signal, caller.getId());
    }

    @MessageMapping("/call.answer")
    public void answerCall(@Payload CallSignal signal, Principal principal) {
        if (principal == null) return;
        User responder = findUserByPrincipal(principal);
        if (responder == null) return;

        // Route the reply back to the caller while keeping receiverId as the responder.
        signal.setReceiverId(responder.getId());
        if (Boolean.TRUE.equals(signal.getAccept())) {
            registerCallAnswer(signal, responder);
        } else {
            handleCallRejected(signal);
        }
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
        User caller = findUserByPrincipal(principal);
        if (caller != null) {
            signal.setCallerId(caller.getId());
            signal.setCallerName(caller.getUsername());
            signal.setCallerAvatar(caller.getAvatarUrl());
        }
        handleCallCancel(signal);
        forwardToTarget(signal);
    }

    @MessageMapping("/call.hangup")
    public void hangupCall(@Payload CallSignal signal, Principal principal) {
        if (principal == null) return;
        User caller = findUserByPrincipal(principal);
        if (caller == null) return;
        signal.setCallerId(caller.getId());
        signal.setCallerName(caller.getUsername());
        signal.setCallerAvatar(caller.getAvatarUrl());
        if ("LEAVE".equalsIgnoreCase(signal.getSignalType())) {
            handleGroupCallLeave(signal, caller);
        } else if ("HANGUP".equalsIgnoreCase(signal.getSignalType())) {
            handlePrivateCallHangup(signal, caller);
        }
        forwardToTarget(signal);
    }

    private void registerCallInvite(CallSignal signal, User caller) {
        if (signal.getCallId() == null) return;

        Conversation conversation = conversationRepository.findById(signal.getConversationId()).orElse(null);
        if (conversation == null) return;

        CallSession session = new CallSession();
        session.callId = signal.getCallId();
        session.conversationId = conversation.getId();
        session.scope = conversation.getType() == ConversationType.GROUP ? "GROUP" : "PRIVATE";
        session.type = signal.getType();
        session.startedAt = LocalDateTime.now();
        session.initiatorId = caller.getId();
        session.participantIds.add(caller.getId());
        session.participantIdsSnapshot.add(caller.getId());
        activeCallSessions.put(signal.getCallId(), session);
    }

    private void registerCallAnswer(CallSignal signal, User responder) {
        if (signal.getCallId() == null) return;
        CallSession session = activeCallSessions.get(signal.getCallId());
        if (session == null) return;
        session.participantIds.add(responder.getId());
        session.participantIdsSnapshot.add(responder.getId());
        session.hadAcceptedParticipant = true;
    }

    private void handleCallRejected(CallSignal signal) {
        if (signal.getCallId() == null || !"rejected".equalsIgnoreCase(signal.getReason())) return;
        CallSession session = activeCallSessions.remove(signal.getCallId());
        if (session == null || session.hadAcceptedParticipant) return;
        createCallHistory(session, "REJECTED", LocalDateTime.now());
    }

    private void handleCallCancel(CallSignal signal) {
        if (signal.getCallId() == null) return;
        CallSession session = activeCallSessions.remove(signal.getCallId());
        if (session == null || session.hadAcceptedParticipant) return;
        String status = "timeout".equalsIgnoreCase(signal.getReason()) ? "MISSED" : "CANCELED";
        createCallHistory(session, status, LocalDateTime.now());
    }

    private void handleGroupCallLeave(CallSignal signal, User leaver) {
        if (signal.getReceiverId() != null || signal.getCallId() == null) return;
        CallSession session = activeCallSessions.get(signal.getCallId());
        if (session == null) return;

        session.participantIdsSnapshot.add(leaver.getId());
        session.participantIds.remove(leaver.getId());
        session.hadAcceptedParticipant = session.hadAcceptedParticipant || !session.initiatorId.equals(leaver.getId());

        if (session.participantIds.isEmpty()) {
            activeCallSessions.remove(signal.getCallId());
            createCallHistory(session, session.hadAcceptedParticipant ? "ENDED" : "MISSED", LocalDateTime.now());
        }
    }

    private void handlePrivateCallHangup(CallSignal signal, User leaver) {
        if (signal.getCallId() == null) return;
        CallSession session = activeCallSessions.remove(signal.getCallId());
        if (session == null) return;

        session.participantIdsSnapshot.add(leaver.getId());
        session.participantIds.remove(leaver.getId());
        createCallHistory(session, session.hadAcceptedParticipant ? "ENDED" : "MISSED", LocalDateTime.now());
    }

    private void createCallHistory(CallSession session, String status, LocalDateTime endedAt) {
        Conversation conversation = conversationRepository.findById(session.conversationId).orElse(null);
        if (conversation == null) return;

        User actor = userRepository.findById(session.initiatorId)
                .orElseGet(() -> conversation.getMembers().stream()
                        .min(Comparator.comparing(User::getUsername))
                        .orElse(null));
        if (actor == null) return;

        LinkedHashSet<String> participantIds = new LinkedHashSet<>(session.participantIdsSnapshot);
        participantIds.addAll(session.participantIds);
        List<User> participants = new ArrayList<>(userRepository.findAllById(participantIds));
        participants.sort(Comparator.comparing(User::getUsername));

        long durationSeconds = Math.max(0, Duration.between(session.startedAt, endedAt).getSeconds());
        boolean groupCall = "GROUP".equals(session.scope);
        String content = switch (status) {
            case "MISSED" -> groupCall ? "Cuoc goi nhom bi nho" : "Cuoc goi bi nho";
            case "REJECTED" -> groupCall ? "Cuoc goi nhom bi tu choi" : "Cuoc goi bi tu choi";
            case "CANCELED" -> groupCall ? "Cuoc goi nhom da huy" : "Cuoc goi da huy";
            default -> groupCall ? "Cuoc goi nhom da ket thuc" : "Cuoc goi da ket thuc";
        };

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("systemType", "CALL_HISTORY");
        metadata.put("callId", session.callId);
        metadata.put("conversationId", session.conversationId);
        metadata.put("callScope", session.scope != null ? session.scope : "PRIVATE");
        metadata.put("callType", session.type != null ? session.type : "VOICE");
        metadata.put("status", status);
        metadata.put("startedAt", session.startedAt.toString());
        metadata.put("endedAt", endedAt.toString());
        metadata.put("durationSeconds", durationSeconds);
        metadata.put("participantCount", participants.size());
        metadata.put("participants", participants.stream()
                .map(this::toParticipantMetadata)
                .toList());

        messageService.createAndBroadcastCallHistoryMessage(conversation, actor, content, metadata);
    }

    private Map<String, Object> toParticipantMetadata(User user) {
        Map<String, Object> participant = new HashMap<>();
        participant.put("id", user.getId());
        participant.put("username", user.getUsername());
        participant.put("avatarUrl", user.getAvatarUrl());
        return participant;
    }

    private static class CallSession {
        private String callId;
        private String conversationId;
        private String scope;
        private String type;
        private String initiatorId;
        private LocalDateTime startedAt;
        private boolean hadAcceptedParticipant = false;
        private final LinkedHashSet<String> participantIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> participantIdsSnapshot = new LinkedHashSet<>();
    }

    private User findUserByPrincipal(Principal principal) {
        String name = principal.getName();
        return userRepository.findByEmail(name)
                .or(() -> userRepository.findByUsername(name))
                .orElse(null);
    }

    private String resolveAgoraConfig(String key, String configuredValue) {
        if (configuredValue != null && !configuredValue.isBlank()) {
            return configuredValue.trim();
        }

        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }

        for (Path envPath : List.of(Path.of(".env"), Path.of("NexTalk_BE", ".env"))) {
            String value = readEnvFileValue(envPath, key);
            if (!value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private String readEnvFileValue(Path envPath, String key) {
        if (!Files.exists(envPath)) {
            return "";
        }
        try {
            return Files.readAllLines(envPath).stream()
                    .map(String::trim)
                    .filter(line -> line.startsWith(key + "="))
                    .map(line -> line.substring((key + "=").length()).trim())
                    .findFirst()
                    .orElse("");
        } catch (IOException ignored) {
            return "";
        }
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

    @PostMapping("/reject")
    public ResponseEntity<ApiResponse<Void>> rejectCallRest(@RequestBody CallSignal signal, Principal principal) {
        if (principal == null) return ResponseEntity.badRequest().build();
        User responder = findUserByPrincipal(principal);
        if (responder == null) return ResponseEntity.badRequest().build();

        signal.setReceiverId(responder.getId());
        handleCallRejected(signal);

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
        return ResponseEntity.ok(ApiResponse.success(null, "Call rejected"));
    }

    private void forwardCallSignal(CallSignal signal, String senderId) {
        Conversation conversation = conversationRepository.findById(signal.getConversationId()).orElse(null);
        if (conversation == null) return;

        List<String> offlineMemberTokens = new ArrayList<>();

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

                if (("INVITE".equalsIgnoreCase(signal.getSignalType()) || "CANCEL".equalsIgnoreCase(signal.getSignalType())) && member.getFcmTokens() != null) {
                    offlineMemberTokens.addAll(member.getFcmTokens());
                }
            }
        }

        if (!offlineMemberTokens.isEmpty()) {
            if ("CANCEL".equalsIgnoreCase(signal.getSignalType())) {
                fcmService.sendCallCancelPushNotificationToTokens(offlineMemberTokens, signal.getCallId());
            } else {
                fcmService.sendCallPushNotificationToTokens(offlineMemberTokens, signal.getCallerName(), signal.getConversationId(), signal.getCallId(), signal.getCallerId());
            }
        }
    }
}
