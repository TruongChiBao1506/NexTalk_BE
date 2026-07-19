package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.FCMService;

import com.google.auth.oauth2.GoogleCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.SendResponse;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FCMServiceImpl implements FCMService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserRepository userRepository;

    @PostConstruct
    public void initialize() {
        try {
            String firebaseCreds = System.getenv("FIREBASE_CREDENTIALS");
            GoogleCredentials credentials = null;

            if (firebaseCreds != null && !firebaseCreds.isEmpty()) {
                // Read from environment variable (useful for Production)
                byte[] credsBytes;
                if (firebaseCreds.trim().startsWith("{")) {
                    credsBytes = firebaseCreds.getBytes();
                } else {
                    // Try decoding from Base64 if it's not raw JSON
                    credsBytes = java.util.Base64.getDecoder().decode(firebaseCreds.trim());
                }
                credentials = GoogleCredentials.fromStream(new java.io.ByteArrayInputStream(credsBytes));
            } else {
                // Read from local file (useful for Development)
                ClassPathResource resource = new ClassPathResource("firebase-service-account.json");
                if (resource.exists()) {
                    credentials = GoogleCredentials.fromStream(resource.getInputStream());
                }
            }

            if (credentials != null) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build();

                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseApp.initializeApp(options);
                    log.info("Firebase application has been initialized");
                }
            } else {
                log.warn("Firebase credentials not found. FCM will be disabled.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Firebase: " + e.getMessage(), e);
        }
    }

    public void sendPushNotificationToTokens(List<String> tokens, String title, String body) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }

        for (String token : tokens.stream().filter(java.util.Objects::nonNull).distinct().toList()) {
            if (isExpoPushToken(token)) {
                sendExpoPushNotification(token, title, body);
                continue;
            }

            if (FirebaseApp.getApps().isEmpty()) {
                continue;
            }

            try {
                Message message = Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .setAndroidConfig(com.google.firebase.messaging.AndroidConfig.builder()
                                .setPriority(com.google.firebase.messaging.AndroidConfig.Priority.HIGH)
                                .setNotification(com.google.firebase.messaging.AndroidNotification.builder()
                                        .setChannelId("messages")
                                        .setSound("default")
                                        .setDefaultSound(true)
                                        .setDefaultVibrateTimings(true)
                                        .build())
                                .build())
                        .build();

                String response = FirebaseMessaging.getInstance().send(message);
                log.info("Successfully sent FCM message: {}", response);
            } catch (Exception e) {
                log.error("Failed to send FCM message: {}", e.getMessage());
            }
        }
    }

    @Override
    public void sendChatPushNotificationToTokens(List<String> tokens, String conversationId, String conversationName,
                                                  String senderId, String senderName, String senderAvatarUrl, String body) {
        if (tokens == null || tokens.isEmpty() || FirebaseApp.getApps().isEmpty()) return;
        for (String token : tokens) {
            if (isExpoPushToken(token)) {
                sendExpoPushNotification(token, senderName, body);
                continue;
            }
            try {
                String safeSenderName = senderName != null && !senderName.isBlank() ? senderName : "NexTalk";
                String notificationTitle = conversationName != null && !conversationName.isBlank()
                        ? safeSenderName + " · " + conversationName
                        : safeSenderName;
                String notificationBody = body != null ? body : "Bạn có tin nhắn mới";
                Message message = Message.builder()
                        .setToken(token)
                        .putData("type", "CHAT_MESSAGE")
                        .putData("conversationId", conversationId != null ? conversationId : "")
                        .putData("conversationName", conversationName != null ? conversationName : "")
                        .putData("senderId", senderId != null ? senderId : "")
                        .putData("senderName", safeSenderName)
                        .putData("senderAvatarUrl", senderAvatarUrl != null ? senderAvatarUrl : "")
                        .putData("body", notificationBody)
                        // Android must receive a data-only push so the client can choose
                        // between a system bubble and a regular notification per device.
                        .setAndroidConfig(com.google.firebase.messaging.AndroidConfig.builder()
                                .setPriority(com.google.firebase.messaging.AndroidConfig.Priority.HIGH)
                                .setTtl(86_400_000L)
                                .setCollapseKey("chat-" + (conversationId != null ? conversationId : "nextalk"))
                                .build())
                        // iOS keeps a normal APNs alert; bubbles are Android-only.
                        .setApnsConfig(ApnsConfig.builder()
                                .putHeader("apns-priority", "10")
                                .setAps(Aps.builder()
                                        .setAlert(ApsAlert.builder()
                                                .setTitle(notificationTitle)
                                                .setBody(notificationBody)
                                                .build())
                                        .setSound("default")
                                        .build())
                                .build())
                        .build();
                String response = FirebaseMessaging.getInstance().send(message);
                log.info("Successfully sent chat FCM message: {}", response);
            } catch (FirebaseMessagingException e) {
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    removeInvalidToken(token);
                    log.info("Removed an unregistered FCM token");
                } else {
                    log.error("Failed to send chat FCM message: {}", e.getMessage());
                }
            } catch (Exception e) {
                log.error("Failed to send chat FCM message: {}", e.getMessage());
            }
        }
    }

    private void removeInvalidToken(String token) {
        userRepository.findAllByFcmTokensContaining(token).forEach(user -> {
            if (user.getFcmTokens() != null && user.getFcmTokens().removeIf(token::equals)) {
                userRepository.save(user);
            }
        });
    }

    @Override
    public void sendCallPushNotificationToTokens(List<String> tokens, String callerName, String conversationId,
                                                 String callId, String callerId, String callerAvatar, String receiverId,
                                                 String callType, String groupName) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }

        if (FirebaseApp.getApps().isEmpty()) return;
        List<String> nativeTokens = tokens.stream()
                .filter(java.util.Objects::nonNull)
                .filter(token -> !isExpoPushToken(token))
                .distinct()
                .toList();
        for (int offset = 0; offset < nativeTokens.size(); offset += 500) {
            List<String> batchTokens = nativeTokens.subList(offset, Math.min(offset + 500, nativeTokens.size()));
            try {
                MulticastMessage message = MulticastMessage.builder()
                        .addAllTokens(batchTokens)
                        .putData("type", "CALL")
                        .putData("callerName", callerName != null ? callerName : "Ai đó")
                        .putData("conversationId", conversationId != null ? conversationId : "")
                        .putData("callId", callId != null ? callId : "")
                        .putData("callerId", callerId != null ? callerId : "")
                        .putData("callerAvatar", callerAvatar != null ? callerAvatar : "")
                        .putData("receiverId", receiverId != null ? receiverId : "")
                        .putData("callType", callType != null ? callType : "VOICE")
                        .putData("groupName", groupName != null ? groupName : "")
                        .setAndroidConfig(com.google.firebase.messaging.AndroidConfig.builder()
                                .setPriority(com.google.firebase.messaging.AndroidConfig.Priority.HIGH)
                                // An unanswered invite is valid only for the ringing window.
                                // The collapse key also lets a later cancel replace an invite
                                // that is still queued for an offline Android device.
                                .setTtl(60_000L)
                                .setCollapseKey("call-" + (callId != null ? callId : "unknown"))
                                .build())
                        .build();

                BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
                removeInvalidBatchTokens(batchTokens, response);
                log.info("Sent call notification to {} device(s); {} failed",
                        response.getSuccessCount(), response.getFailureCount());
            } catch (Exception e) {
                log.error("Failed to send call notification: {}", e.getMessage());
            }
        }
    }

    @Override
    public void sendCallCancelPushNotificationToTokens(List<String> tokens, String callId) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }

        if (FirebaseApp.getApps().isEmpty()) return;
        List<String> nativeTokens = tokens.stream()
                .filter(java.util.Objects::nonNull)
                .filter(token -> !isExpoPushToken(token))
                .distinct()
                .toList();
        for (int offset = 0; offset < nativeTokens.size(); offset += 500) {
            List<String> batchTokens = nativeTokens.subList(offset, Math.min(offset + 500, nativeTokens.size()));
            try {
                MulticastMessage message = MulticastMessage.builder()
                        .addAllTokens(batchTokens)
                        .putData("type", "CALL_CANCEL")
                        .putData("callId", callId != null ? callId : "")
                        .setAndroidConfig(com.google.firebase.messaging.AndroidConfig.builder()
                                .setPriority(com.google.firebase.messaging.AndroidConfig.Priority.HIGH)
                                // Keep the cancellation slightly longer than the invite so a
                                // delayed invite cannot resurrect an already-ended call.
                                .setTtl(120_000L)
                                .setCollapseKey("call-" + (callId != null ? callId : "unknown"))
                                .build())
                        .build();

                BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
                removeInvalidBatchTokens(batchTokens, response);
                log.info("Sent call cancellation to {} device(s); {} failed",
                        response.getSuccessCount(), response.getFailureCount());
            } catch (Exception e) {
                log.error("Failed to send call cancellation: {}", e.getMessage());
            }
        }
    }

    private void removeInvalidBatchTokens(List<String> tokens, BatchResponse response) {
        List<SendResponse> responses = response.getResponses();
        for (int index = 0; index < responses.size(); index++) {
            SendResponse sendResponse = responses.get(index);
            if (sendResponse.isSuccessful() || sendResponse.getException() == null) continue;
            if (sendResponse.getException().getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                removeInvalidToken(tokens.get(index));
            }
        }
    }

    private boolean isExpoPushToken(String token) {
        return token != null && (token.startsWith("ExponentPushToken[") || token.startsWith("ExpoPushToken["));
    }

    private void sendExpoPushNotification(String token, String title, String body) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "to", token,
                    "title", title,
                    "body", body,
                    "sound", "default"
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(EXPO_PUSH_URL))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Successfully sent Expo push message");
            } else {
                log.warn("Failed to send Expo push message. Status: {}, Body: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Failed to send Expo push message: {}", e.getMessage());
        }
    }
}
