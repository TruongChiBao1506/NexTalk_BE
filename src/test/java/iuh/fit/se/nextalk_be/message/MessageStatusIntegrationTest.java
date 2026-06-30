package iuh.fit.se.nextalk_be.message;

import iuh.fit.se.nextalk_be.dto.request.MessageRequest;
import iuh.fit.se.nextalk_be.dto.request.MessageStatusUpdateRequest;
import iuh.fit.se.nextalk_be.dto.response.MessageStatusUpdateResponse;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageStatus;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.MessageStatusRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
// @Transactional
public class MessageStatusIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessageStatusRepository messageStatusRepository;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User senderUser;
    private User receiverUser;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        messageStatusRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        senderUser = User.builder()
                .email("sender@gmail.com")
                .username("senderuser")
                .password("password123")
                .isVerified(true)
                .build();
        senderUser = userRepository.save(senderUser);

        receiverUser = User.builder()
                .email("receiver@gmail.com")
                .username("receiveruser")
                .password("password123")
                .isVerified(true)
                .build();
        receiverUser = userRepository.save(receiverUser);

        conversation = Conversation.builder()
                .type(ConversationType.PRIVATE)
                .members(Set.of(senderUser, receiverUser))
                .build();
        conversation = conversationRepository.save(conversation);
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void sendMessage_InitializesStatusAsSentForReceiver() throws Exception {
        MessageRequest request = MessageRequest.builder()
                .conversationId(conversation.getId())
                .content("Hello receiver")
                .messageType("TEXT")
                .build();

        mockMvc.perform(post("/api/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Verify that a message was saved
        List<Message> messages = messageRepository.findAll();
        assertEquals(1, messages.size());
        Message savedMessage = messages.get(0);
        org.junit.jupiter.api.Assertions.assertNotNull(savedMessage.getCreatedAt());

        // Verify that a status record was created for the receiver as SENT
        List<MessageStatus> statuses = messageStatusRepository.findAll();
        assertEquals(1, statuses.size());
        MessageStatus statusRecord = statuses.get(0);
        assertEquals(savedMessage.getId(), statusRecord.getMessage().getId());
        assertEquals(receiverUser.getId(), statusRecord.getUser().getId());
        assertEquals("SENT", statusRecord.getStatus());

        // Verify WebSocket broadcast to sender and receiver
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq("senderuser"), eq("/queue/private"), any()
        );
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq("receiveruser"), eq("/queue/private"), any()
        );
    }

    @Test
    @WithMockUser(username = "receiver@gmail.com")
    void markAsDelivered_Success() throws Exception {
        // First, sender sends a message
        Message message = Message.builder()
                .conversation(conversation)
                .sender(senderUser)
                .content("Hello receiver")
                .messageType(MessageType.TEXT)
                .build();
        Message savedMessage = messageRepository.save(message);

        // Save SENT status for receiver
        MessageStatus statusRecord = MessageStatus.builder()
                .message(savedMessage)
                .user(receiverUser)
                .status("SENT")
                .build();
        messageStatusRepository.save(statusRecord);

        // Perform Mark as Delivered
        MessageStatusUpdateRequest request = MessageStatusUpdateRequest.builder()
                .conversationId(conversation.getId())
                .build();

        mockMvc.perform(post("/api/messages/status/delivered")
                        .principal(() -> "receiver@gmail.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Verify updated status in DB
        List<MessageStatus> statuses = messageStatusRepository.findAll();
        assertEquals(1, statuses.size());
        assertEquals("DELIVERED", statuses.get(0).getStatus());

        // Verify WebSocket broadcast to the other member (sender)
        ArgumentCaptor<MessageStatusUpdateResponse> captor = ArgumentCaptor.forClass(MessageStatusUpdateResponse.class);
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq("senderuser"), eq("/queue/private"), captor.capture()
        );
        MessageStatusUpdateResponse broadcastResponse = captor.getValue();
        assertEquals("DELIVERED", broadcastResponse.getStatus());
        assertEquals(conversation.getId(), broadcastResponse.getConversationId());
        assertEquals(receiverUser.getId(), broadcastResponse.getUserId());
        assertEquals("receiveruser", broadcastResponse.getUsername());
    }

    @Test
    @WithMockUser(username = "receiver@gmail.com")
    void markAsSeen_Success() throws Exception {
        // First, sender sends a message
        Message message = Message.builder()
                .conversation(conversation)
                .sender(senderUser)
                .content("Hello receiver")
                .messageType(MessageType.TEXT)
                .build();
        Message savedMessage = messageRepository.save(message);

        // Save SENT status for receiver
        MessageStatus statusRecord = MessageStatus.builder()
                .message(savedMessage)
                .user(receiverUser)
                .status("SENT")
                .build();
        messageStatusRepository.save(statusRecord);

        // Perform Mark as Seen
        MessageStatusUpdateRequest request = MessageStatusUpdateRequest.builder()
                .conversationId(conversation.getId())
                .build();

        mockMvc.perform(post("/api/messages/status/seen")
                        .principal(() -> "receiver@gmail.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Verify updated status in DB
        List<MessageStatus> statuses = messageStatusRepository.findAll();
        assertEquals(1, statuses.size());
        assertEquals("SEEN", statuses.get(0).getStatus());

        // Verify WebSocket broadcast to the other member (sender)
        ArgumentCaptor<MessageStatusUpdateResponse> captor = ArgumentCaptor.forClass(MessageStatusUpdateResponse.class);
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq("senderuser"), eq("/queue/private"), captor.capture()
        );
        MessageStatusUpdateResponse broadcastResponse = captor.getValue();
        assertEquals("SEEN", broadcastResponse.getStatus());
        assertEquals(conversation.getId(), broadcastResponse.getConversationId());
        assertEquals(receiverUser.getId(), broadcastResponse.getUserId());
        assertEquals("receiveruser", broadcastResponse.getUsername());
    }

    @Test
    @WithMockUser(username = "receiver@gmail.com")
    void markAsDelivered_NotMember_ThrowsBadRequestException() throws Exception {
        // Create another conversation where receiver is NOT a member
        User otherUser = User.builder()
                .email("other@gmail.com")
                .username("otheruser")
                .password("password123")
                .isVerified(true)
                .build();
        otherUser = userRepository.save(otherUser);

        Conversation otherConversation = Conversation.builder()
                .type(ConversationType.PRIVATE)
                .members(Set.of(senderUser, otherUser))
                .build();
        otherConversation = conversationRepository.save(otherConversation);

        MessageStatusUpdateRequest request = MessageStatusUpdateRequest.builder()
                .conversationId(otherConversation.getId())
                .build();

        mockMvc.perform(post("/api/messages/status/delivered")
                        .principal(() -> "receiver@gmail.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
