package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationNotificationMode;
import iuh.fit.se.nextalk_be.entity.ConversationNotificationSetting;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ConversationNotificationPreferenceService {

    public EffectivePreference resolve(Conversation conversation, String userId) {
        ConversationNotificationSetting setting = conversation.getNotificationSettingsByUser() == null
                ? null
                : conversation.getNotificationSettingsByUser().get(userId);

        if (setting != null && setting.getMode() != null) {
            Instant mutedUntil = setting.getMutedUntil();
            if (mutedUntil == null || mutedUntil.isAfter(Instant.now())) {
                return new EffectivePreference(setting.getMode(), mutedUntil);
            }
        }

        // Backward compatibility for conversations muted before detailed settings existed.
        if (setting == null
                && conversation.getMutedByUsers() != null
                && conversation.getMutedByUsers().contains(userId)) {
            return new EffectivePreference(ConversationNotificationMode.NONE, null);
        }

        return new EffectivePreference(ConversationNotificationMode.ALL, null);
    }

    public boolean shouldNotify(Conversation conversation, String userId, boolean mentionedOrReplied) {
        ConversationNotificationMode mode = resolve(conversation, userId).mode();
        return mode == ConversationNotificationMode.ALL
                || (mode == ConversationNotificationMode.MENTIONS_ONLY && mentionedOrReplied);
    }

    public record EffectivePreference(ConversationNotificationMode mode, Instant mutedUntil) {
    }
}
