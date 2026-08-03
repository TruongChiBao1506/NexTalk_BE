package iuh.fit.se.nextalk_be.message;

import iuh.fit.se.nextalk_be.dto.request.MessageRequest;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.entity.MessageStatus;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.ConversationUnreadMarkerRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.MessageStatusRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
// @Transactional
public class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationUnreadMarkerRepository conversationUnreadMarkerRepository;

    @Autowired
    private MessageStatusRepository messageStatusRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User senderUser;
    private User receiverUser;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversationUnreadMarkerRepository.deleteAll();
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
    void sendMessage_Success() throws Exception {
        MessageRequest request = MessageRequest.builder()
                .conversationId(conversation.getId())
                .content("Hello World")
                .messageType("TEXT")
                .build();

        mockMvc.perform(post("/api/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", is("Hello World")))
                .andExpect(jsonPath("$.data.messageType", is("TEXT")));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void getConversationMessages_Success() throws Exception {
        Message message = Message.builder()
                .conversation(conversation)
                .sender(senderUser)
                .content("Test message")
                .messageType(MessageType.TEXT)
                .build();
        messageRepository.save(message);

        mockMvc.perform(get("/api/messages/" + conversation.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].content", is("Test message")));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void cursorHistory_DoesNotDuplicateOrSkipWhenNewMessageArrives() throws Exception {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 10, 0);
        List<Message> originals = new java.util.ArrayList<>();
        for (int index = 0; index < 4; index++) {
            Message message = Message.builder()
                    .conversation(conversation)
                    .conversationId(conversation.getId())
                    .sender(senderUser)
                    .senderId(senderUser.getId())
                    .senderUsername(senderUser.getUsername())
                    .content("History fixture")
                    .messageType(MessageType.TEXT)
                    .metadata(Map.of("clientMessageId", "cursor-history-" + index))
                    .build();
            message.setCreatedAt(timestamp);
            originals.add(messageRepository.save(message));
        }

        String firstPayload = mockMvc.perform(get("/api/messages/" + conversation.getId() + "/history")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.hasMore", is(true)))
                .andExpect(jsonPath("$.data.nextCursor", not(emptyOrNullString())))
                .andReturn().getResponse().getContentAsString();
        var firstPage = objectMapper.readTree(firstPayload).at("/data/items");
        String cursor = objectMapper.readTree(firstPayload).at("/data/nextCursor").asText();

        Message newest = Message.builder()
                .conversation(conversation)
                .conversationId(conversation.getId())
                .sender(senderUser)
                .senderId(senderUser.getId())
                .senderUsername(senderUser.getUsername())
                .content("New history fixture")
                .messageType(MessageType.TEXT)
                .metadata(Map.of("clientMessageId", "cursor-history-new"))
                .build();
        newest.setCreatedAt(timestamp.plusMinutes(1));
        messageRepository.save(newest);

        String secondPayload = mockMvc.perform(get("/api/messages/" + conversation.getId() + "/history")
                        .param("limit", "2")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.hasMore", is(false)))
                .andReturn().getResponse().getContentAsString();
        var secondPage = objectMapper.readTree(secondPayload).at("/data/items");

        Set<String> receivedIds = new HashSet<>();
        firstPage.forEach(node -> receivedIds.add(node.get("id").asText()));
        secondPage.forEach(node -> receivedIds.add(node.get("id").asText()));
        org.junit.jupiter.api.Assertions.assertEquals(4, receivedIds.size());
        org.junit.jupiter.api.Assertions.assertEquals(
                originals.stream().map(Message::getId).collect(java.util.stream.Collectors.toSet()),
                receivedIds);
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void aroundMessage_ReturnsBoundedDirectContext() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 1, 2, 10, 0);
        Message anchor = null;
        for (int index = 0; index < 41; index++) {
            Message message = Message.builder()
                    .conversation(conversation)
                    .conversationId(conversation.getId())
                    .sender(senderUser)
                    .senderId(senderUser.getId())
                    .senderUsername(senderUser.getUsername())
                    .content("Context fixture " + index)
                    .messageType(MessageType.TEXT)
                    .metadata(Map.of("clientMessageId", "around-message-" + index))
                    .build();
            message.setCreatedAt(base.plusSeconds(index));
            message = messageRepository.save(message);
            if (index == 20) anchor = message;
        }

        mockMvc.perform(get("/api/messages/" + conversation.getId() + "/around/" + anchor.getId())
                        .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(25)))
                .andExpect(jsonPath("$.data.anchorMessageId", is(anchor.getId())))
                .andExpect(jsonPath("$.data.hasMore", is(true)))
                .andExpect(jsonPath("$.data.hasNewer", is(true)));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void pinnedMessages_UseStableCursorPagination() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 1, 3, 10, 0);
        for (int index = 0; index < 3; index++) {
            Message message = Message.builder()
                    .conversation(conversation)
                    .conversationId(conversation.getId())
                    .sender(senderUser)
                    .senderId(senderUser.getId())
                    .senderUsername(senderUser.getUsername())
                    .content("Pinned fixture")
                    .messageType(MessageType.TEXT)
                    .isPinned(true)
                    .metadata(Map.of("clientMessageId", "pinned-cursor-" + index))
                    .build();
            message.setCreatedAt(base.plusSeconds(index));
            messageRepository.save(message);
        }

        String firstPayload = mockMvc.perform(get("/api/conversations/" + conversation.getId() + "/pinned/cursor")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.hasMore", is(true)))
                .andReturn().getResponse().getContentAsString();
        String cursor = objectMapper.readTree(firstPayload).at("/data/nextCursor").asText();

        mockMvc.perform(get("/api/conversations/" + conversation.getId() + "/pinned/cursor")
                        .param("limit", "2")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.hasMore", is(false)));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void advancedSearch_FiltersAndPaginatesVisibleMessages() throws Exception {
        for (int index = 0; index < 3; index++) {
            messageRepository.save(Message.builder()
                    .conversation(conversation)
                    .conversationId(conversation.getId())
                    .sender(senderUser)
                    .senderId(senderUser.getId())
                    .senderUsername(senderUser.getUsername())
                    .content("Quarterly plan " + index)
                    .messageType(MessageType.TEXT)
                    .isRecalled(false)
                    .metadata(Map.of("clientMessageId", "advanced-search-" + index))
                    .build());
        }

        mockMvc.perform(get("/api/messages/search/advanced")
                        .param("query", "Quarterly")
                        .param("conversationId", conversation.getId())
                        .param("senderId", senderUser.getId())
                        .param("messageType", "TEXT")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements", is(3)))
                .andExpect(jsonPath("$.data.hasMore", is(true)));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void cursorSearch_UsesTextIndexAndStableBoundary() throws Exception {
        LocalDateTime base = LocalDateTime.of(2026, 1, 4, 10, 0);
        for (int index = 0; index < 3; index++) {
            Message message = Message.builder()
                    .conversation(conversation)
                    .conversationId(conversation.getId())
                    .sender(senderUser)
                    .senderId(senderUser.getId())
                    .senderUsername(senderUser.getUsername())
                    .content("searchfixture reference")
                    .messageType(MessageType.TEXT)
                    .metadata(Map.of("clientMessageId", "search-cursor-" + index))
                    .build();
            message.setCreatedAt(base.plusSeconds(index));
            messageRepository.save(message);
        }

        String firstPayload = mockMvc.perform(get("/api/messages/search/cursor")
                        .param("query", "searchfixture")
                        .param("conversationId", conversation.getId())
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.hasMore", is(true)))
                .andReturn().getResponse().getContentAsString();
        String cursor = objectMapper.readTree(firstPayload).at("/data/nextCursor").asText();

        Message newlyMatched = Message.builder()
                .conversation(conversation)
                .conversationId(conversation.getId())
                .sender(senderUser)
                .senderId(senderUser.getId())
                .senderUsername(senderUser.getUsername())
                .content("searchfixture newly indexed")
                .messageType(MessageType.TEXT)
                .metadata(Map.of("clientMessageId", "search-cursor-new"))
                .build();
        newlyMatched.setCreatedAt(base.plusMinutes(1));
        messageRepository.save(newlyMatched);

        mockMvc.perform(get("/api/messages/search/cursor")
                        .param("query", "searchfixture")
                        .param("conversationId", conversation.getId())
                        .param("limit", "2")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.hasMore", is(false)));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void markConversationUnread_PersistsWithoutDowngradingReadReceipts() throws Exception {
        Message message = messageRepository.save(Message.builder()
                .conversation(conversation)
                .conversationId(conversation.getId())
                .sender(receiverUser)
                .senderId(receiverUser.getId())
                .senderUsername(receiverUser.getUsername())
                .content("Follow up later")
                .messageType(MessageType.TEXT)
                .isRecalled(false)
                .metadata(Map.of("clientMessageId", "mark-unread-message"))
                .build());

        mockMvc.perform(post("/api/messages/" + conversation.getId() + "/unread")
                        .principal(() -> "sender@gmail.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageId", is(message.getId())))
                .andExpect(jsonPath("$.data.unreadCount", is(1)));

        mockMvc.perform(get("/api/messages/unread-counts")
                        .principal(() -> "sender@gmail.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data['" + conversation.getId() + "']", is(1)));

        mockMvc.perform(post("/api/messages/status/seen")
                        .principal(() -> "sender@gmail.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + conversation.getId() + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/messages/unread-counts")
                        .principal(() -> "sender@gmail.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data['" + conversation.getId() + "']").doesNotExist());
    }

    @Test
    @WithMockUser(username = "outsider@gmail.com")
    void advancedSearch_RejectsConversationOutsider() throws Exception {
        userRepository.save(User.builder()
                .email("outsider@gmail.com")
                .username("outsider")
                .password("password123")
                .isVerified(true)
                .build());

        Message visibleOnlyToMembers = messageRepository.save(Message.builder()
                .conversation(conversation)
                .conversationId(conversation.getId())
                .sender(senderUser)
                .senderId(senderUser.getId())
                .senderUsername(senderUser.getUsername())
                .content("Access-control fixture")
                .messageType(MessageType.TEXT)
                .metadata(Map.of("clientMessageId", "around-access-control"))
                .build());

        mockMvc.perform(get("/api/messages/search/advanced")
                        .param("query", "private")
                        .param("conversationId", conversation.getId()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/messages/" + conversation.getId() + "/unread")
                        .principal(() -> "outsider@gmail.com"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/messages/" + conversation.getId() + "/around/" + visibleOnlyToMembers.getId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void deliveryDetails_ReturnsPaginatedRecipientSummaryToSender() throws Exception {
        Message message = messageRepository.save(Message.builder()
                .conversation(conversation)
                .conversationId(conversation.getId())
                .sender(senderUser)
                .senderId(senderUser.getId())
                .senderUsername(senderUser.getUsername())
                .content("Delivery detail")
                .messageType(MessageType.TEXT)
                .metadata(Map.of("clientMessageId", "delivery-detail-message"))
                .build());
        messageStatusRepository.save(MessageStatus.builder()
                .message(message)
                .messageId(message.getId())
                .conversationId(conversation.getId())
                .user(receiverUser)
                .userId(receiverUser.getId())
                .status("SEEN")
                .build());

        mockMvc.perform(get("/api/messages/" + message.getId() + "/delivery-details")
                        .param("status", "SEEN")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seenCount", is(1)))
                .andExpect(jsonPath("$.data.deliveredCount", is(0)))
                .andExpect(jsonPath("$.data.sentCount", is(0)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].username", is("receiveruser")))
                .andExpect(jsonPath("$.data.items[0].status", is("SEEN")));
    }

    @Test
    @WithMockUser(username = "receiver@gmail.com")
    void deliveryDetails_RejectsNonSender() throws Exception {
        Message message = messageRepository.save(Message.builder()
                .conversation(conversation)
                .conversationId(conversation.getId())
                .sender(senderUser)
                .senderId(senderUser.getId())
                .senderUsername(senderUser.getUsername())
                .content("Private receipt detail")
                .messageType(MessageType.TEXT)
                .metadata(Map.of("clientMessageId", "delivery-detail-denied"))
                .build());

        mockMvc.perform(get("/api/messages/" + message.getId() + "/delivery-details"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void syncConversationMessages_UsesCursorAndReturnsDeletionTombstone() throws Exception {
        Message message = Message.builder()
                .conversation(conversation)
                .sender(senderUser)
                .content("Cached message")
                .messageType(MessageType.TEXT)
                .build();
        message = messageRepository.save(message);

        String initialCursor = objectMapper.readTree(mockMvc.perform(get("/api/messages/" + conversation.getId() + "/sync")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullSnapshot", is(true)))
                .andExpect(jsonPath("$.data.messages", hasSize(1)))
                .andExpect(jsonPath("$.data.cursor", not(emptyOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString())
                .at("/data/cursor")
                .asText();

        mockMvc.perform(get("/api/messages/" + conversation.getId() + "/sync")
                        .param("since", initialCursor)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullSnapshot", is(false)));

        mockMvc.perform(get("/api/messages/" + conversation.getId() + "/sync")
                        .param("since", "2000-01-01T00:00:00")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullSnapshot", is(false)))
                .andExpect(jsonPath("$.data.messages", hasSize(1)))
                .andExpect(jsonPath("$.data.messages[0].id", is(message.getId())))
                .andExpect(jsonPath("$.data.deletedMessageIds", hasSize(0)));

        mockMvc.perform(delete("/api/messages/" + message.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/messages/" + conversation.getId() + "/sync")
                        .param("since", "2000-01-01T00:00:00")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages", hasSize(0)))
                .andExpect(jsonPath("$.data.deletedMessageIds", hasSize(1)))
                .andExpect(jsonPath("$.data.deletedMessageIds[0]", is(message.getId())));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void syncConversationMessages_RespectsRequestedInitialSnapshotLimit() throws Exception {
        for (int index = 0; index < 30; index++) {
            messageRepository.save(Message.builder()
                    .conversation(conversation)
                    .conversationId(conversation.getId())
                    .sender(senderUser)
                    .senderId(senderUser.getId())
                    .senderUsername(senderUser.getUsername())
                    .content("Snapshot message " + index)
                    .messageType(MessageType.TEXT)
                    .metadata(Map.of("clientMessageId", "snapshot-" + index))
                    .build());
        }

        mockMvc.perform(get("/api/messages/" + conversation.getId() + "/sync")
                        .param("limit", "12")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullSnapshot", is(true)))
                .andExpect(jsonPath("$.data.messages", hasSize(12)));
    }
}
