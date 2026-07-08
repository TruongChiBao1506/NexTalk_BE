package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.User;

public interface AiBotService {
    void answerMentionAsync(Conversation conversation, Message triggerMessage, User requester);
}
