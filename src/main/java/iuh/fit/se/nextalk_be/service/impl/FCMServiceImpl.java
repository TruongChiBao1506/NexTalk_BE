package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.FCMService;

import com.google.auth.oauth2.GoogleCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
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
public class FCMServiceImpl implements FCMService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                        .build();

                String response = FirebaseMessaging.getInstance().send(message);
                log.info("Successfully sent FCM message: {}", response);
            } catch (Exception e) {
                log.error("Failed to send FCM message to token: " + token, e);
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
