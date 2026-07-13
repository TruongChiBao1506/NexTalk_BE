package iuh.fit.se.nextalk_be.config;

import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageStatus;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

@Component
public class DenormalizedMessageFieldsCallback implements BeforeConvertCallback<Object> {
    @Override
    public Object onBeforeConvert(Object source, String collection) {
        if (source instanceof Message message) {
            if (message.getConversation() != null) message.setConversationId(message.getConversation().getId());
            if (message.getSender() != null) {
                message.setSenderId(message.getSender().getId());
                message.setSenderUsername(message.getSender().getUsername());
            }
        } else if (source instanceof MessageStatus status) {
            if (status.getMessage() != null) {
                status.setMessageId(status.getMessage().getId());
                status.setConversationId(status.getMessage().getConversationId() != null
                        ? status.getMessage().getConversationId()
                        : status.getMessage().getConversation().getId());
            }
            if (status.getUser() != null) status.setUserId(status.getUser().getId());
        }
        return source;
    }
}
