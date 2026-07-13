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
import com.google.firebase.messaging.Notification;
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

        for (String token : tokens) {
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
                log.error("Failed to send FCM message to token: " + token, e);
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
                Notification.Builder notificationBuilder = Notification.builder()
                        .setTitle(conversationName != null && !conversationName.isBlank()
                                ? senderName + " · " + conversationName
                                : senderName)
                        .setBody(body != null ? body : "Bạn có tin nhắn mới");
                if (senderAvatarUrl != null && !senderAvatarUrl.isBlank()) {
                    notificationBuilder.setImage(senderAvatarUrl);
                }
                Message message = Message.builder()
                        .setToken(token)
                        // Android can render this even when the JS process is not alive.
                        .setNotification(notificationBuilder.build())
                        .putData("type", "CHAT_MESSAGE")
                        .putData("conversationId", conversationId != null ? conversationId : "")
                        .putData("conversationName", conversationName != null ? conversationName : "")
                        .putData("senderId", senderId != null ? senderId : "")
                        .putData("senderName", senderName != null ? senderName : "NexTalk")
                        .putData("senderAvatarUrl", senderAvatarUrl != null ? senderAvatarUrl : "")
                        .putData("body", body != null ? body : "Bạn có tin nhắn mới")
                        .setAndroidConfig(com.google.firebase.messaging.AndroidConfig.builder()
                                .setPriority(com.google.firebase.messaging.AndroidConfig.Priority.HIGH)
                                .setTtl(86_400_000L)
                                .setNotification(com.google.firebase.messaging.AndroidNotification.builder()
                                        .setChannelId("messages")
                                        .setSound("default")
                                        .setDefaultVibrateTimings(true)
                                        .build())
                                .build())
                        .build();
                String response = FirebaseMessaging.getInstance().send(message);
                log.info("Successfully sent chat FCM message: {}", response);
            } catch (FirebaseMessagingException e) {
                if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    removeInvalidToken(token);
                    log.info("Removed an unregistered FCM token ending in {}", tokenSuffix(token));
                } else {
                    log.error("Failed to send chat FCM message to token ending in {}: {}",
                            tokenSuffix(token), e.getMessage(), e);
                }
            } catch (Exception e) {
                log.error("Failed to send chat FCM message to token ending in {}: {}",
                        tokenSuffix(token), e.getMessage(), e);
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

    private String tokenSuffix(String token) {
        if (token == null || token.isBlank()) return "unknown";
        return token.substring(Math.max(0, token.length() - 8));
    }

    @Override
    public void sendCallPushNotificationToTokens(List<String> tokens, String callerName, String channelId, String callId, String callerId) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }

        for (String token : tokens) {
            if (isExpoPushToken(token)) {
                // Not supported for pure data messages to backgrounded apps without Expo push payload
                continue;
            }

            if (FirebaseApp.getApps().isEmpty()) {
                continue;
            }

            try {
                Message message = Message.builder()
                        .setToken(token)
                        .putData("type", "CALL")
                        .putData("callerName", callerName != null ? callerName : "Ai đó")
                        .putData("channelId", channelId != null ? channelId : "")
                        .putData("callId", callId != null ? callId : "")
                        .putData("callerId", callerId != null ? callerId : "")
                        .setAndroidConfig(com.google.firebase.messaging.AndroidConfig.builder()
                                .setPriority(com.google.firebase.messaging.AndroidConfig.Priority.HIGH)
                                .build())
                        .build();

                String response = FirebaseMessaging.getInstance().send(message);
                log.info("Successfully sent FCM Data message for call: {}", response);
            } catch (Exception e) {
                log.error("Failed to send FCM Data message for call to token: " + token, e);
            }
        }
    }

    @Override
    public void sendCallCancelPushNotificationToTokens(List<String> tokens, String callId) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }

        for (String token : tokens) {
            if (FirebaseApp.getApps().isEmpty()) {
                continue;
            }

            try {
                Message message = Message.builder()
                        .setToken(token)
                        .putData("type", "CALL_CANCEL")
                        .putData("callId", callId != null ? callId : "")
                        .setAndroidConfig(com.google.firebase.messaging.AndroidConfig.builder()
                                .setPriority(com.google.firebase.messaging.AndroidConfig.Priority.HIGH)
                                .build())
                        .build();

                String response = FirebaseMessaging.getInstance().send(message);
                log.info("Successfully sent FCM Cancel message for call: {}", response);
            } catch (Exception e) {
                log.error("Failed to send FCM Cancel message for call to token: " + token, e);
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
                log.info("Successfully sent Expo push message to token: {}", token);
            } else {
                log.warn("Failed to send Expo push message. Status: {}, Body: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Failed to send Expo push message to token: " + token, e);
        }
    }
}
