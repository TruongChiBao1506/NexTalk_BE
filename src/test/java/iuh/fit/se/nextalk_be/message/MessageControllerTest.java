package iuh.fit.se.nextalk_be.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.nextalk_be.conversation.Conversation;
import iuh.fit.se.nextalk_be.conversation.ConversationRepository;
import iuh.fit.se.nextalk_be.conversation.ConversationType;
import iuh.fit.se.nextalk_be.message.dto.MessageRequest;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
public class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User senderUser;
    private User receiverUser;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
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
}
