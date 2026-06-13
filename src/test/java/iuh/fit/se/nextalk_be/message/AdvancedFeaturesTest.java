package iuh.fit.se.nextalk_be.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.nextalk_be.conversation.Conversation;
import iuh.fit.se.nextalk_be.conversation.ConversationRepository;
import iuh.fit.se.nextalk_be.conversation.ConversationType;
import iuh.fit.se.nextalk_be.group.Group;
import iuh.fit.se.nextalk_be.group.GroupMember;
import iuh.fit.se.nextalk_be.group.GroupMemberRepository;
import iuh.fit.se.nextalk_be.group.GroupRepository;
import iuh.fit.se.nextalk_be.group.GroupRole;
import iuh.fit.se.nextalk_be.message.dto.CreatePollRequest;
import iuh.fit.se.nextalk_be.message.dto.EditMessageRequest;
import iuh.fit.se.nextalk_be.message.dto.MessageRequest;
import iuh.fit.se.nextalk_be.message.dto.ReactMessageRequest;
import iuh.fit.se.nextalk_be.user.User;
import iuh.fit.se.nextalk_be.user.UserRepository;
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

import java.util.Set;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
// @Transactional
public class AdvancedFeaturesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User senderUser;
    private User receiverUser;
    private Conversation conversation;
    private Message baseMessage;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
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

        baseMessage = Message.builder()
                .conversation(conversation)
                .sender(senderUser)
                .content("Initial Content")
                .messageType(MessageType.TEXT)
                .build();
        baseMessage = messageRepository.save(baseMessage);
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void replyMessage_Success() throws Exception {
        MessageRequest request = MessageRequest.builder()
                .conversationId(conversation.getId())
                .content("Reply Content")
                .messageType("TEXT")
                .parentId(baseMessage.getId())
                .build();

        mockMvc.perform(post("/api/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.parentId", is(baseMessage.getId().toString())));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void editMessage_Success() throws Exception {
        EditMessageRequest request = EditMessageRequest.builder()
                .content("Edited Content")
                .build();

        mockMvc.perform(put("/api/messages/" + baseMessage.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", is("Edited Content")))
                .andExpect(jsonPath("$.data.isEdited", is(true)));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void recallMessage_Success() throws Exception {
        mockMvc.perform(post("/api/messages/" + baseMessage.getId() + "/recall")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.isRecalled", is(true)))
                .andExpect(jsonPath("$.data.content", is("Tin nhắn đã bị thu hồi")));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void deleteMessageForMe_Success() throws Exception {
        // Delete message for sender
        mockMvc.perform(delete("/api/messages/" + baseMessage.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        // Retrieve messages as sender - should be empty
        mockMvc.perform(get("/api/messages/" + conversation.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void pinMessage_Success() throws Exception {
        // Pin message
        mockMvc.perform(post("/api/messages/" + baseMessage.getId() + "/pin")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.isPinned", is(true)));

        // Get pinned messages
        mockMvc.perform(get("/api/conversations/" + conversation.getId() + "/pinned")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(baseMessage.getId().toString())));

        // Unpin message
        mockMvc.perform(delete("/api/messages/" + baseMessage.getId() + "/pin")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPinned", is(false)));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void createPoll_IsNotPinnedByDefault_AndCanBePinnedManually() throws Exception {
        Conversation groupConversation = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .members(Set.of(senderUser, receiverUser))
                .build());
        Group group = groupRepository.save(Group.builder()
                .name("Poll Group")
                .owner(senderUser)
                .conversation(groupConversation)
                .build());
        groupMemberRepository.save(GroupMember.builder()
                .group(group)
                .user(senderUser)
                .role(GroupRole.LEADER)
                .build());
        groupMemberRepository.save(GroupMember.builder()
                .group(group)
                .user(receiverUser)
                .role(GroupRole.MEMBER)
                .build());

        CreatePollRequest request = CreatePollRequest.builder()
                .conversationId(groupConversation.getId())
                .question("Where should we go?")
                .options(List.of("Cafe", "Park"))
                .build();

        String responseBody = mockMvc.perform(post("/api/messages/polls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.messageType", is("POLL")))
                .andExpect(jsonPath("$.data.isPinned", is(false)))
                .andExpect(jsonPath("$.data.pinnedAt", nullValue()))
                .andReturn().getResponse().getContentAsString();

        String pollId = objectMapper.readTree(responseBody).at("/data/id").asText();

        mockMvc.perform(post("/api/messages/" + pollId + "/pin")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPinned", is(true)));

        mockMvc.perform(delete("/api/messages/" + pollId + "/pin")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPinned", is(false)));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void reactToMessage_Success() throws Exception {
        ReactMessageRequest request = ReactMessageRequest.builder()
                .emoji("❤️")
                .build();

        // Add reaction
        mockMvc.perform(post("/api/messages/" + baseMessage.getId() + "/react")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.reactions", hasSize(1)))
                .andExpect(jsonPath("$.data.reactions[0].emoji", is("❤️")));

        // Remove reaction (toggle)
        mockMvc.perform(post("/api/messages/" + baseMessage.getId() + "/react")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reactions", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void searchUsers_Success() throws Exception {
        mockMvc.perform(get("/api/users/search")
                        .param("query", "receiver")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].username", is("receiveruser")));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void searchConversations_Success() throws Exception {
        mockMvc.perform(get("/api/conversations/search")
                        .param("query", "receiver")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(conversation.getId().toString())));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void searchMessages_Success() throws Exception {
        mockMvc.perform(get("/api/messages/search")
                        .param("query", "Initial")
                        .param("conversationId", conversation.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].content", is("Initial Content")));
    }
}
