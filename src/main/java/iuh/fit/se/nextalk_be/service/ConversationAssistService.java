package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.response.BirthdayContextResponse;
import iuh.fit.se.nextalk_be.dto.response.ReplySuggestionsResponse;

public interface ConversationAssistService {
    ReplySuggestionsResponse suggestReplies(String conversationId, String requestedLastMessageId);

    BirthdayContextResponse getBirthdayContext(String conversationId);

    ReplySuggestionsResponse personalizeBirthdayWishes(String conversationId, String requestedLastMessageId);
}
