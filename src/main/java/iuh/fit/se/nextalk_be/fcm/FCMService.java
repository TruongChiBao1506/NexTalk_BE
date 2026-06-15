package iuh.fit.se.nextalk_be.fcm;

import com.google.auth.oauth2.GoogleCredentials;
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
import java.util.List;

@Service
@Slf4j
public class FCMService {

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
        if (tokens == null || tokens.isEmpty() || FirebaseApp.getApps().isEmpty()) {
            return;
        }

        for (String token : tokens) {
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
}
