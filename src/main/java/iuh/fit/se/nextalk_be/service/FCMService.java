package iuh.fit.se.nextalk_be.service;

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

public interface FCMService {
    @PostConstruct public void initialize();
    public void sendPushNotificationToTokens(List<String> tokens, String title, String body);
    void sendChatPushNotificationToTokens(List<String> tokens, String conversationId, String conversationName,
                                          String senderId, String senderName, String senderAvatarUrl, String body);
    void sendCallPushNotificationToTokens(List<String> tokens, String callerName, String conversationId,
                                          String callId, String callerId, String callerAvatar, String receiverId,
                                          String callType, String groupName);
    void sendCallCancelPushNotificationToTokens(List<String> tokens, String callId);
}
