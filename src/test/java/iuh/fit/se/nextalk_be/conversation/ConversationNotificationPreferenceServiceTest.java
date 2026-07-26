package iuh.fit.se.nextalk_be.conversation;

import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationNotificationMode;
import iuh.fit.se.nextalk_be.entity.ConversationNotificationSetting;
import iuh.fit.se.nextalk_be.service.ConversationNotificationPreferenceService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationNotificationPreferenceServiceTest {
    private final ConversationNotificationPreferenceService service =
            new ConversationNotificationPreferenceService();

    @Test
    void defaultsToAllNotifications() {
        Conversation conversation = Conversation.builder().build();

        assertEquals(ConversationNotificationMode.ALL, service.resolve(conversation, "user-1").mode());
        assertTrue(service.shouldNotify(conversation, "user-1", false));
    }

    @Test
    void keepsLegacyMuteCompatible() {
        Conversation conversation = Conversation.builder()
                .mutedByUsers(new HashSet<>(Set.of("user-1")))
                .build();

        assertEquals(ConversationNotificationMode.NONE, service.resolve(conversation, "user-1").mode());
        assertFalse(service.shouldNotify(conversation, "user-1", true));
    }

    @Test
    void mentionsOnlyAllowsMentionNotifications() {
        Conversation conversation = Conversation.builder()
                .notificationSettingsByUser(new HashMap<>(Map.of(
                        "user-1",
                        ConversationNotificationSetting.builder()
                                .mode(ConversationNotificationMode.MENTIONS_ONLY)
                                .build()
                )))
                .build();

        assertFalse(service.shouldNotify(conversation, "user-1", false));
        assertTrue(service.shouldNotify(conversation, "user-1", true));
    }

    @Test
    void expiredTemporaryMuteFallsBackToAll() {
        Conversation conversation = Conversation.builder()
                .notificationSettingsByUser(new HashMap<>(Map.of(
                        "user-1",
                        ConversationNotificationSetting.builder()
                                .mode(ConversationNotificationMode.NONE)
                                .mutedUntil(Instant.now().minusSeconds(1))
                                .build()
                )))
                .build();

        assertEquals(ConversationNotificationMode.ALL, service.resolve(conversation, "user-1").mode());
        assertTrue(service.shouldNotify(conversation, "user-1", false));
    }
}
