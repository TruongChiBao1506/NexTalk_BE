package iuh.fit.se.nextalk_be.conversation;

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
import java.util.UUID;

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
public class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    private User currentUser;
    private User friendUser;

    @BeforeEach
    void setUp() {
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        currentUser = User.builder()
                .email("current@gmail.com")
                .username("currentuser")
                .password("password123")
                .isVerified(true)
                .build();
        currentUser = userRepository.save(currentUser);

        friendUser = User.builder()
                .email("friend@gmail.com")
                .username("frienduser")
                .password("password123")
                .isVerified(true)
                .build();
        friendUser = userRepository.save(friendUser);
    }

    @Test
    @WithMockUser(username = "current@gmail.com")
    void getOrCreatePrivateConversation_Success() throws Exception {
        mockMvc.perform(post("/api/conversations/private/" + friendUser.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.type", is("PRIVATE")))
                .andExpect(jsonPath("$.data.members", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "current@gmail.com")
    void getUserConversations_Success() throws Exception {
        Conversation conversation = Conversation.builder()
                .type(ConversationType.PRIVATE)
                .members(Set.of(currentUser, friendUser))
                .build();
        conversationRepository.save(conversation);

        mockMvc.perform(get("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @WithMockUser(username = "current@gmail.com")
    void getConversationById_Success() throws Exception {
        Conversation conversation = Conversation.builder()
                .type(ConversationType.PRIVATE)
                .members(Set.of(currentUser, friendUser))
                .build();
        conversation = conversationRepository.save(conversation);

        mockMvc.perform(get("/api/conversations/" + conversation.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.type", is("PRIVATE")));
    }

    @Test
    @WithMockUser(username = "current@gmail.com")
    void getConversationById_NotFound() throws Exception {
        UUID randomId = UUID.randomUUID();
        mockMvc.perform(get("/api/conversations/" + randomId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }
}
