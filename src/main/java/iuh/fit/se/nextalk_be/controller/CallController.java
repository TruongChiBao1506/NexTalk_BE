package iuh.fit.se.nextalk_be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.nextalk_be.dto.CallSignal;
import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.CallTokenResponse;
import iuh.fit.se.nextalk_be.entity.Channel;
import iuh.fit.se.nextalk_be.entity.ChannelType;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.event.VoiceChannelEvent;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.GroupMemberRepository;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.Principal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallController {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ChannelRepository channelRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;
    private final VoiceChannelService voiceChannelService;
    private final FCMService fcmService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Map<String, CallSession> activeCallSessions = new ConcurrentHashMap<>();
    private final Map<String, Long> closedCallSessions = new ConcurrentHashMap<>();

    private static final String CALL_SESSION_KEY_PREFIX = "nextalk:call:session:";

    @Value("${app.agora.app-id}")
    private String appId;

    @Value("${app.agora.app-certificate}")
    private String appCertificate;

    @Value("${app.calls.ringing-ttl:75s}")
    private Duration ringingCallTtl;

    @Value("${app.calls.connected-ttl:2h}")
    private Duration connectedCallTtl;

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
        requireVoiceChannelAccess(channelId, groupId, currentUser);
        
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

    @GetMapping("/channel-members")
    public ResponseEntity<ApiResponse<List<String>>> getVoiceChannelMembers(
            @RequestParam("channelId") String channelId
    ) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        requireVoiceChannelAccess(channelId, null, currentUser);
        return ResponseEntity.ok(ApiResponse.success(
                voiceChannelService.getChannelMembers(channelId),
                "Voice channel members retrieved successfully"
        ));
    }

    @MessageMapping("/voice.join")
    public void joinVoiceChannel(@Payload VoiceChannelEvent event, Principal principal) {
        if (principal == null) return;
        User user = findUserByPrincipal(principal);
        if (user == null) return;
        if (!hasVoiceChannelAccess(event.getChannelId(), event.getGroupId(), user)) return;

        // A user can move directly between voice channels. Notify listeners of
        // the old channel as well, otherwise clients keep rendering a ghost
        // member until they reload their presence snapshot.
        String[] previousChannelInfo = voiceChannelService.leaveCurrentChannel(user.getId());
        if (previousChannelInfo != null && !previousChannelInfo[0].equals(event.getChannelId())) {
            String previousChannelId = previousChannelInfo[0];
            String previousGroupId = previousChannelInfo[1];
            VoiceChannelEvent leaveEvent = VoiceChannelEvent.builder()
                    .type("LEAVE")
                    .channelId(previousChannelId)
                    .groupId(previousGroupId)
                    .userId(user.getId())
                    .currentMembers(voiceChannelService.getChannelMembers(previousChannelId))
                    .build();
            messagingTemplate.convertAndSend("/topic/group." + previousGroupId + ".voice", leaveEvent);
        }

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

        if (!registerCallInvite(signal, caller)) return;
        forwardCallSignal(signal, caller.getId());
    }

    @MessageMapping("/call.answer")
    public void answerCall(@Payload CallSignal signal, Principal principal) {
        if (principal == null) return;
        User responder = findUserByPrincipal(principal);
        if (responder == null) return;

        // Route the reply back to the caller while keeping receiverId as the responder.
        signal.setReceiverId(responder.getId());
        if (registerCallResponse(signal, responder, null) != CallResponseOutcome.ACCEPTED) return;
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
        notifyOtherResponderDevices(signal, responder, null);
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
        if (!handleCallCancel(signal, caller)) return;
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
        boolean handled = false;
        if ("LEAVE".equalsIgnoreCase(signal.getSignalType())) {
            handled = handleGroupCallLeave(signal, caller);
        } else if ("HANGUP".equalsIgnoreCase(signal.getSignalType())) {
            handled = handlePrivateCallHangup(signal, caller);
        }
        if (!handled) return;
        forwardToTarget(signal);
    }

    private boolean registerCallInvite(CallSignal signal, User caller) {
        if (signal.getCallId() == null || signal.getConversationId() == null) return false;
        Long closedUntil = closedCallSessions.get(signal.getCallId());
        if (closedUntil != null && closedUntil > System.currentTimeMillis()) return false;
        if (getCallSession(signal.getCallId()) != null) return false;

        Conversation conversation = conversationRepository.findById(signal.getConversationId()).orElse(null);
        if (conversation == null || !isConversationMember(conversation, caller.getId())) return false;

        if (signal.getReceiverId() != null && conversation.getMembers().stream()
                .noneMatch(member -> member.getId().equals(signal.getReceiverId()))) {
            return false;
        }

        CallSession session = new CallSession();
        session.callId = signal.getCallId();
        session.conversationId = conversation.getId();
        session.scope = conversation.getType() == ConversationType.GROUP ? "GROUP" : "PRIVATE";
        session.type = signal.getType();
        session.startedAt = LocalDateTime.now();
        session.initiatorId = caller.getId();
        session.participantIds.add(caller.getId());
        session.participantIdsSnapshot.add(caller.getId());
        conversation.getMembers().stream()
                .map(User::getId)
                .filter(memberId -> !memberId.equals(caller.getId()))
                .filter(memberId -> signal.getReceiverId() == null || memberId.equals(signal.getReceiverId()))
                .forEach(session.invitedResponderIds::add);
        if (activeCallSessions.putIfAbsent(signal.getCallId(), session) != null) return false;
        persistCallSession(session, ringingCallTtl);
        return true;
    }

    private CallResponseOutcome registerCallResponse(CallSignal signal, User responder, String responseDeviceKey) {
        if (signal.getCallId() == null || signal.getConversationId() == null
                || signal.getCallerId() == null || signal.getAccept() == null) {
            return CallResponseOutcome.INVALID;
        }
        CallSession session = getCallSession(signal.getCallId());
        if (session == null
                || !signal.getConversationId().equals(session.conversationId)
                || !signal.getCallerId().equals(session.initiatorId)
                || (signal.getType() != null && session.type != null
                && !signal.getType().equalsIgnoreCase(session.type))) return CallResponseOutcome.INVALID;

        Conversation conversation = conversationRepository.findById(session.conversationId).orElse(null);
        if (conversation == null || conversation.getMembers().stream().noneMatch(member -> member.getId().equals(responder.getId()))) {
            return CallResponseOutcome.INVALID;
        }
        if (!session.invitedResponderIds.contains(responder.getId())) {
            return CallResponseOutcome.INVALID;
        }

        synchronized (session) {
            if (activeCallSessions.get(signal.getCallId()) != session
                    || closedCallSessions.containsKey(signal.getCallId())) {
                return CallResponseOutcome.ALREADY_HANDLED;
            }
            if (session.respondedUserIds.contains(responder.getId())) {
                boolean sameAnswer = Objects.equals(session.responseAcceptedByUser.get(responder.getId()), signal.getAccept());
                boolean sameDevice = responseDeviceKey != null
                        && responseDeviceKey
                        .equals(session.responseDeviceByUser.get(responder.getId()));
                return sameAnswer && sameDevice
                        ? CallResponseOutcome.DUPLICATE_SAME_DEVICE
                        : CallResponseOutcome.ALREADY_HANDLED;
            }

            session.respondedUserIds.add(responder.getId());
            session.responseAcceptedByUser.put(responder.getId(), Boolean.TRUE.equals(signal.getAccept()));
            if (responseDeviceKey != null && !responseDeviceKey.isBlank()) {
                session.responseDeviceByUser.put(responder.getId(), responseDeviceKey);
            }
            if (Boolean.TRUE.equals(signal.getAccept())) {
                session.participantIds.add(responder.getId());
                session.participantIdsSnapshot.add(responder.getId());
                session.hadAcceptedParticipant = true;
                if (session.connectedAt == null) session.connectedAt = LocalDateTime.now();
            } else {
                session.rejectedUserIds.add(responder.getId());
            }
            persistCallSession(session, session.hadAcceptedParticipant ? connectedCallTtl : ringingCallTtl);
        }
        if (!Boolean.TRUE.equals(signal.getAccept())) handleCallRejected(signal, session);
        return CallResponseOutcome.ACCEPTED;
    }

    private void handleCallRejected(CallSignal signal, CallSession session) {
        if (signal.getCallId() == null || "GROUP".equals(session.scope)) return;
        CallSession removed = removeKnownCallSession(signal.getCallId(), session);
        if (removed == null || removed.hadAcceptedParticipant) return;
        String status = "missed".equalsIgnoreCase(signal.getReason()) ? "MISSED" : "REJECTED";
        createCallHistory(removed, status, LocalDateTime.now());
    }

    private boolean handleCallCancel(CallSignal signal, User caller) {
        if (signal.getCallId() == null) return false;
        CallSession current = getCallSession(signal.getCallId());
        if (current == null) return false;
        CallSession session;
        synchronized (current) {
            if (!current.initiatorId.equals(caller.getId())
                    || !current.conversationId.equals(signal.getConversationId())
                    || current.hadAcceptedParticipant) return false;
            session = removeKnownCallSession(signal.getCallId(), current);
        }
        if (session == null) return false;
        String status = "timeout".equalsIgnoreCase(signal.getReason()) ? "MISSED" : "CANCELED";
        createCallHistory(session, status, LocalDateTime.now());
        return true;
    }

    private boolean handleGroupCallLeave(CallSignal signal, User leaver) {
        if (signal.getReceiverId() != null || signal.getCallId() == null) return false;
        CallSession session = getCallSession(signal.getCallId());
        if (session == null) return false;

        CallSession removed = null;
        synchronized (session) {
            if (activeCallSessions.get(signal.getCallId()) != session
                    || !"GROUP".equals(session.scope)
                    || !session.conversationId.equals(signal.getConversationId())
                    || !session.participantIds.contains(leaver.getId())) return false;
            session.participantIdsSnapshot.add(leaver.getId());
            session.participantIds.remove(leaver.getId());
            session.hadAcceptedParticipant = session.hadAcceptedParticipant || !session.initiatorId.equals(leaver.getId());
            if (session.participantIds.isEmpty()) {
                removed = removeKnownCallSession(signal.getCallId(), session);
            } else {
                persistCallSession(session, connectedCallTtl);
            }
        }
        if (removed != null) createCallHistory(removed, removed.hadAcceptedParticipant ? "ENDED" : "MISSED", LocalDateTime.now());
        return true;
    }

    private boolean handlePrivateCallHangup(CallSignal signal, User leaver) {
        if (signal.getCallId() == null) return false;
        CallSession current = getCallSession(signal.getCallId());
        if (current == null) return false;
        CallSession session;
        synchronized (current) {
            if (activeCallSessions.get(signal.getCallId()) != current
                    || !"PRIVATE".equals(current.scope)
                    || !current.conversationId.equals(signal.getConversationId())
                    || !current.participantIds.contains(leaver.getId())) return false;
            session = removeKnownCallSession(signal.getCallId(), current);
        }
        if (session == null) return false;

        session.participantIdsSnapshot.add(leaver.getId());
        session.participantIds.remove(leaver.getId());
        createCallHistory(session, session.hadAcceptedParticipant ? "ENDED" : "MISSED", LocalDateTime.now());
        return true;
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

        LocalDateTime durationStart = session.connectedAt != null ? session.connectedAt : endedAt;
        long durationSeconds = Math.max(0, Duration.between(durationStart, endedAt).getSeconds());
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

    private enum CallResponseOutcome {
        ACCEPTED,
        DUPLICATE_SAME_DEVICE,
        ALREADY_HANDLED,
        INVALID
    }

    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    private static class CallSession {
        private String callId;
        private String conversationId;
        private String scope;
        private String type;
        private String initiatorId;
        private LocalDateTime startedAt;
        private LocalDateTime connectedAt;
        private boolean hadAcceptedParticipant = false;
        private LinkedHashSet<String> respondedUserIds = new LinkedHashSet<>();
        private LinkedHashSet<String> rejectedUserIds = new LinkedHashSet<>();
        private LinkedHashSet<String> invitedResponderIds = new LinkedHashSet<>();
        private LinkedHashSet<String> participantIds = new LinkedHashSet<>();
        private LinkedHashSet<String> participantIdsSnapshot = new LinkedHashSet<>();
        private Map<String, Boolean> responseAcceptedByUser = new HashMap<>();
        private Map<String, String> responseDeviceByUser = new HashMap<>();
    }

    @Scheduled(fixedDelayString = "${app.calls.cleanup-delay-ms:15000}")
    public void expireStaleCallSessions() {
        LocalDateTime now = LocalDateTime.now();
        activeCallSessions.forEach((callId, session) -> expireCallSessionIfStale(callId, session, now));
        long epochMillis = System.currentTimeMillis();
        closedCallSessions.entrySet().removeIf(entry -> entry.getValue() <= epochMillis);
    }

    private void expireCallSessionIfStale(String callId, CallSession session, LocalDateTime now) {
        synchronized (session) {
            LocalDateTime reference = session.hadAcceptedParticipant && session.connectedAt != null
                    ? session.connectedAt
                    : session.startedAt;
            Duration ttl = session.hadAcceptedParticipant ? connectedCallTtl : ringingCallTtl;
            if (reference == null || Duration.between(reference, now).compareTo(ttl) < 0) return;
            markCallSessionClosed(callId);
            deletePersistedCallSession(callId);
            if (!activeCallSessions.remove(callId, session)) return;
        }

        String status = session.hadAcceptedParticipant ? "ENDED" : "MISSED";
        createCallHistory(session, status, now);
        if (!session.hadAcceptedParticipant) {
            CallSignal timeoutSignal = CallSignal.builder()
                    .callId(session.callId)
                    .conversationId(session.conversationId)
                    .callerId(session.initiatorId)
                    .receiverId("PRIVATE".equals(session.scope)
                            ? session.invitedResponderIds.stream().findFirst().orElse(null)
                            : null)
                    .type(session.type)
                    .signalType("CANCEL")
                    .reason("timeout")
                    .build();
            forwardCallSignal(timeoutSignal, session.initiatorId);
        }
    }

    private boolean isConversationMember(Conversation conversation, String userId) {
        return conversation.getMembers() != null && conversation.getMembers().stream()
                .anyMatch(member -> member.getId().equals(userId));
    }

    private Channel requireVoiceChannelAccess(String channelId, String expectedGroupId, User user) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("Voice channel not found"));
        if (!hasVoiceChannelAccess(channel, expectedGroupId, user)) {
            throw new BadRequestException("You are not allowed to access this voice channel");
        }
        return channel;
    }

    private boolean hasVoiceChannelAccess(String channelId, String expectedGroupId, User user) {
        if (channelId == null || user == null) return false;
        return channelRepository.findById(channelId)
                .map(channel -> hasVoiceChannelAccess(channel, expectedGroupId, user))
                .orElse(false);
    }

    private boolean hasVoiceChannelAccess(Channel channel, String expectedGroupId, User user) {
        if (channel.getType() != ChannelType.VOICE || channel.getGroup() == null) return false;
        String groupId = channel.getGroup().getId();
        if (expectedGroupId != null && !expectedGroupId.equals(groupId)) return false;

        boolean groupMember = channel.getGroup().getOwner() != null
                && user.getId().equals(channel.getGroup().getOwner().getId());
        groupMember = groupMember || groupMemberRepository.existsByGroupIdAndUserId(groupId, user.getId());
        if (!groupMember) return false;

        return !channel.isPrivate()
                || (channel.getConversation() != null && isConversationMember(channel.getConversation(), user.getId()));
    }

    private String responseDeviceKey(String deviceId, String fcmToken) {
        if (deviceId != null && !deviceId.isBlank()) {
            return "device:" + deviceId.trim();
        }
        if (fcmToken == null || fcmToken.isBlank()) return null;
        try {
            return "fcm:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(fcmToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
            return "fcm:" + Integer.toHexString(fcmToken.hashCode());
        }
    }

    private String callSessionKey(String callId) {
        return CALL_SESSION_KEY_PREFIX + callId;
    }

    private CallSession getCallSession(String callId) {
        Long closedUntil = closedCallSessions.get(callId);
        if (closedUntil != null && closedUntil > System.currentTimeMillis()) return null;
        CallSession local = activeCallSessions.get(callId);
        if (local != null) return local;
        try {
            String serialized = redisTemplate.opsForValue().get(callSessionKey(callId));
            if (serialized == null) return null;
            CallSession restored = objectMapper.readValue(serialized, CallSession.class);
            CallSession existing = activeCallSessions.putIfAbsent(callId, restored);
            return existing != null ? existing : restored;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void persistCallSession(CallSession session, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(
                    callSessionKey(session.callId),
                    objectMapper.writeValueAsString(session),
                    ttl
            );
        } catch (Exception ignored) {
            // Calls continue in-memory when Redis is temporarily unavailable.
        }
    }

    private CallSession removeKnownCallSession(String callId, CallSession session) {
        markCallSessionClosed(callId);
        deletePersistedCallSession(callId);
        if (!activeCallSessions.remove(callId, session)) return null;
        return session;
    }

    private void markCallSessionClosed(String callId) {
        closedCallSessions.put(callId, System.currentTimeMillis() + connectedCallTtl.toMillis());
    }

    private void deletePersistedCallSession(String callId) {
        try {
            redisTemplate.delete(callSessionKey(callId));
        } catch (Exception ignored) {
            // Redis TTL will remove the fallback copy later.
        }
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
            User receiver = userRepository.findById(signal.getReceiverId()).orElse(null);
            if (receiver != null) {
                messagingTemplate.convertAndSendToUser(
                        receiver.getUsername(),
                        "/queue/calls",
                        signal
                );
                if (("CANCEL".equalsIgnoreCase(signal.getSignalType())
                        || "HANGUP".equalsIgnoreCase(signal.getSignalType()))
                        && receiver.getFcmTokens() != null) {
                    fcmService.sendCallCancelPushNotificationToTokens(receiver.getFcmTokens(), signal.getCallId());
                }
            }
        } else {
            forwardCallSignal(signal, signal.getCallerId());
        }
    }

    @PostMapping("/reject")
    public ResponseEntity<ApiResponse<Void>> rejectCallRest(
            @RequestBody CallSignal signal,
            Principal principal,
            @RequestHeader(value = "X-FCM-Token", required = false) String respondingDeviceToken,
            @RequestHeader(value = "X-Device-ID", required = false) String respondingDeviceId
    ) {
        signal.setAccept(false);
        signal.setReason("rejected");
        return respondToCallRest(signal, principal, respondingDeviceToken, respondingDeviceId);
    }

    @PostMapping("/respond")
    public ResponseEntity<ApiResponse<Void>> respondToCallRest(
            @RequestBody CallSignal signal,
            Principal principal,
            @RequestHeader(value = "X-FCM-Token", required = false) String respondingDeviceToken,
            @RequestHeader(value = "X-Device-ID", required = false) String respondingDeviceId
    ) {
        if (principal == null) return ResponseEntity.badRequest().build();
        User responder = findUserByPrincipal(principal);
        if (responder == null) return ResponseEntity.badRequest().build();

        signal.setReceiverId(responder.getId());
        signal.setSignalType("ANSWER");
        CallResponseOutcome outcome = registerCallResponse(
                signal, responder, responseDeviceKey(respondingDeviceId, respondingDeviceToken));
        if (outcome == CallResponseOutcome.DUPLICATE_SAME_DEVICE) {
            return ResponseEntity.ok(ApiResponse.success(null, "Call response already handled by this device"));
        }
        if (outcome != CallResponseOutcome.ACCEPTED) {
            return ResponseEntity.status(409)
                    .body(ApiResponse.error("Call already handled on another device or expired"));
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
        notifyOtherResponderDevices(signal, responder, respondingDeviceToken, respondingDeviceId);
        return ResponseEntity.ok(ApiResponse.success(null, "Call response handled"));
    }

    private void notifyOtherResponderDevices(CallSignal original, User responder, String respondingDeviceToken) {
        notifyOtherResponderDevices(original, responder, respondingDeviceToken, null);
    }

    private void notifyOtherResponderDevices(CallSignal original, User responder, String respondingDeviceToken,
                                             String respondingDeviceId) {
        CallSignal handledSignal = CallSignal.builder()
                .callId(original.getCallId())
                .conversationId(original.getConversationId())
                .callerId(original.getCallerId())
                .receiverId(responder.getId())
                .type(original.getType())
                .signalType("CALL_HANDLED")
                .accept(original.getAccept())
                .reason(Boolean.TRUE.equals(original.getAccept()) ? "answered_on_another_device" : "rejected_on_another_device")
                .handledByDeviceId(respondingDeviceId)
                .build();

        // User destinations fan out to every active WebSocket session for this user.
        // The device that performed the action is already connected/idle and ignores it;
        // other ringing devices close their incoming-call UI.
        messagingTemplate.convertAndSendToUser(
                responder.getUsername(),
                "/queue/calls",
                handledSignal
        );

        if (responder.getFcmTokens() != null && !responder.getFcmTokens().isEmpty()) {
            List<String> otherDeviceTokens = responder.getFcmTokens().stream()
                    .filter(token -> respondingDeviceToken == null || !respondingDeviceToken.equals(token))
                    .toList();
            fcmService.sendCallCancelPushNotificationToTokens(otherDeviceTokens, original.getCallId());
        }
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
                fcmService.sendCallPushNotificationToTokens(
                        offlineMemberTokens,
                        signal.getCallerName(),
                        signal.getConversationId(),
                        signal.getCallId(),
                        signal.getCallerId(),
                        signal.getCallerAvatar(),
                        signal.getReceiverId(),
                        signal.getType(),
                        signal.getGroupName()
                );
            }
        }
    }
}
