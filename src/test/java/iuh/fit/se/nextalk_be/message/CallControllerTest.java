package iuh.fit.se.nextalk_be.message;

import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;


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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
// @Transactional
public class CallControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    private User memberUser;
    private User nonMemberUser;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        memberUser = User.builder()
                .email("member@gmail.com")
                .username("memberuser")
                .password("password123")
                .isVerified(true)
                .build();
        memberUser = userRepository.save(memberUser);

        nonMemberUser = User.builder()
                .email("nonmember@gmail.com")
                .username("nonmemberuser")
                .password("password123")
                .isVerified(true)
                .build();
        nonMemberUser = userRepository.save(nonMemberUser);

        conversation = Conversation.builder()
                .type(ConversationType.PRIVATE)
                .members(Set.of(memberUser))
                .build();
        conversation = conversationRepository.save(conversation);
    }

    @Test
    @WithMockUser(username = "member@gmail.com")
    void getCallToken_Success() throws Exception {
        mockMvc.perform(get("/api/calls/token")
                        .param("conversationId", conversation.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.token", notNullValue()))
                .andExpect(jsonPath("$.data.channelName", is(conversation.getId().toString())))
                .andExpect(jsonPath("$.data.uid", notNullValue()));
    }

    @Test
    @WithMockUser(username = "nonmember@gmail.com")
    void getCallToken_Failure_NotMember() throws Exception {
        mockMvc.perform(get("/api/calls/token")
                        .param("conversationId", conversation.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }
}
