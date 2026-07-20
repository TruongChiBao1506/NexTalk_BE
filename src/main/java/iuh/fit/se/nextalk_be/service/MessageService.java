package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.request.AddPollOptionRequest;
import iuh.fit.se.nextalk_be.dto.request.CreatePollRequest;
import iuh.fit.se.nextalk_be.dto.request.EditMessageRequest;
import iuh.fit.se.nextalk_be.dto.request.MessageRequest;
import iuh.fit.se.nextalk_be.dto.request.PollVoteRequest;
import iuh.fit.se.nextalk_be.dto.request.ReactMessageRequest;
import iuh.fit.se.nextalk_be.dto.request.ShareMessageRequest;
import iuh.fit.se.nextalk_be.dto.request.TypingIndicatorRequest;
import iuh.fit.se.nextalk_be.dto.response.MessageResponse;
import iuh.fit.se.nextalk_be.dto.response.MessageSyncResponse;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

public interface MessageService {
    public MessageResponse sendMessage(MessageRequest request);
    public MessageResponse sendMessage(MessageRequest request, String senderEmail);
    public void broadcastTypingIndicator(TypingIndicatorRequest request, String senderEmail);
    public Page<MessageResponse> getConversationMessages(String conversationId, Pageable pageable);
    public MessageSyncResponse syncConversationMessages(String conversationId, LocalDateTime since, int limit);
    public List<MessageResponse> getLatestMessages(List<String> conversationIds);
    public Map<String, Long> getUnreadCounts(String username);
    public void markConversationMessagesAsDelivered(String conversationId, String username);
    public void markConversationMessagesAsSeen(String conversationId, String username);
    public void createAndBroadcastCallHistoryMessage(Conversation conversation, User actor, String content, Map<String, Object> metadata);
    public MessageResponse createPoll(CreatePollRequest request);
    public MessageResponse votePoll(String messageId, PollVoteRequest request);
    public MessageResponse addPollOption(String messageId, AddPollOptionRequest request);
    public MessageResponse lockPoll(String messageId);
    public MessageResponse deletePoll(String messageId);
    public MessageResponse editMessage(String messageId, EditMessageRequest request);
    public MessageResponse recallMessage(String messageId);
    public MessageResponse recallAttachment(String messageId, String attachmentUrl);
    public void deleteMessageForMe(String messageId);
    public MessageResponse pinMessage(String messageId, boolean pin);
    public List<MessageResponse> getPinnedMessages(String conversationId);
    public MessageResponse reactToMessage(String messageId, ReactMessageRequest request);
    public List<MessageResponse> shareMessage(String messageId, ShareMessageRequest request);
    public List<MessageResponse> searchMessages(String query, String conversationId);

    // Batch operations
    public void deleteMessagesForMe(List<String> messageIds);
    public List<MessageResponse> recallMessages(List<String> messageIds);
    public List<MessageResponse> shareMessages(iuh.fit.se.nextalk_be.dto.request.BatchShareMessageRequest request);
}
