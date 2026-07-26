package iuh.fit.se.nextalk_be.conversation;

import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.Channel;
import iuh.fit.se.nextalk_be.entity.ChannelType;
import iuh.fit.se.nextalk_be.entity.Group;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.GroupRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
// @Transactional
public class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User currentUser;
    private User friendUser;

    @BeforeEach
    void setUp() {
        channelRepository.deleteAll();
        groupRepository.deleteAll();
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
                .andExpect(jsonPath("$.data.members", hasSize(2)))
                .andExpect(jsonPath("$.data.canSendMessages", is(true)));
    }

    @Test
    @WithMockUser(username = "current@gmail.com")
    void getOrCreatePrivateConversation_WhenReceiverBlocksStrangers_IsRejected() throws Exception {
        friendUser.setBlockStrangerMessages(true);
        friendUser = userRepository.save(friendUser);

        mockMvc.perform(post("/api/conversations/private/" + friendUser.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @WithMockUser(username = "current@gmail.com")
    void getOrCreatePrivateConversation_Twice_ReturnsSameConversation() throws Exception {
        mockMvc.perform(post("/api/conversations/private/" + friendUser.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/conversations/private/" + friendUser.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(1, conversationRepository.count());
    }

    @Test
    @WithMockUser(username = "current@gmail.com")
    void getUserConversations_Success() throws Exception {
        Conversation conversation = Conversation.builder()
                .type(ConversationType.GROUP)
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
    @WithMockUser(username = "friend@gmail.com")
    void getUserConversations_DoesNotShowEmptyPrivateConversationToRecipient() throws Exception {
        conversationRepository.save(Conversation.builder()
                .type(ConversationType.PRIVATE)
                .members(Set.of(currentUser, friendUser))
                .build());

        mockMvc.perform(get("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(0)));
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

    @Test
    @WithMockUser(username = "current@gmail.com")
    void updateHidden_ForGroupChannel_UpdatesOnlySelectedChannel() throws Exception {
        currentUser.setChatPin(passwordEncoder.encode("1234"));
        currentUser = userRepository.save(currentUser);

        Group group = groupRepository.save(Group.builder()
                .name("Project group")
                .owner(currentUser)
                .build());

        Conversation generalConversation = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .members(Set.of(currentUser, friendUser))
                .build());
        Conversation planningConversation = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .members(Set.of(currentUser, friendUser))
                .build());

        channelRepository.save(Channel.builder()
                .name("General")
                .type(ChannelType.TEXT)
                .group(group)
                .conversation(generalConversation)
                .build());
        channelRepository.save(Channel.builder()
                .name("Planning")
                .type(ChannelType.TEXT)
                .group(group)
                .conversation(planningConversation)
                .build());

        mockMvc.perform(put("/api/conversations/" + planningConversation.getId() + "/hidden")
                        .param("hidden", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hidden", is(true)));

        org.junit.jupiter.api.Assertions.assertFalse(conversationRepository.findById(generalConversation.getId())
                .orElseThrow().getHiddenByUsers().contains(currentUser.getId()));
        org.junit.jupiter.api.Assertions.assertTrue(conversationRepository.findById(planningConversation.getId())
                .orElseThrow().getHiddenByUsers().contains(currentUser.getId()));

        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].channels[?(@.name == 'General')].hidden", hasItem(false)))
                .andExpect(jsonPath("$.data[0].channels[?(@.name == 'Planning')].hidden", hasItem(true)));

        mockMvc.perform(put("/api/conversations/" + generalConversation.getId() + "/hidden")
                        .param("hidden", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hidden", is(true)));

        mockMvc.perform(get("/api/conversations/search")
                        .param("query", "1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].id", hasItems(
                        generalConversation.getId(),
                        planningConversation.getId())));

        mockMvc.perform(put("/api/conversations/" + planningConversation.getId() + "/hidden")
                        .param("hidden", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hidden", is(false)));

        org.junit.jupiter.api.Assertions.assertTrue(conversationRepository.findById(generalConversation.getId())
                .orElseThrow().getHiddenByUsers().contains(currentUser.getId()));
        org.junit.jupiter.api.Assertions.assertFalse(conversationRepository.findById(planningConversation.getId())
                .orElseThrow().getHiddenByUsers().contains(currentUser.getId()));
    }

    @Test
    @WithMockUser(username = "current@gmail.com")
    void updatePinned_ForAnyGroupChannel_SynchronizesTheWholeGroup() throws Exception {
        Group group = groupRepository.save(Group.builder()
                .name("Project group")
                .owner(currentUser)
                .build());

        Conversation generalConversation = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .members(Set.of(currentUser, friendUser))
                .build());
        Conversation planningConversation = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .members(Set.of(currentUser, friendUser))
                .build());

        channelRepository.save(Channel.builder()
                .name("General")
                .type(ChannelType.TEXT)
                .group(group)
                .conversation(generalConversation)
                .build());
        channelRepository.save(Channel.builder()
                .name("Planning")
                .type(ChannelType.TEXT)
                .group(group)
                .conversation(planningConversation)
                .build());

        mockMvc.perform(put("/api/conversations/" + planningConversation.getId() + "/pin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pinned", is(true)));

        org.junit.jupiter.api.Assertions.assertTrue(conversationRepository.findById(generalConversation.getId())
                .orElseThrow().getPinnedByUsers().contains(currentUser.getId()));
        org.junit.jupiter.api.Assertions.assertTrue(conversationRepository.findById(planningConversation.getId())
                .orElseThrow().getPinnedByUsers().contains(currentUser.getId()));

        mockMvc.perform(delete("/api/conversations/" + generalConversation.getId() + "/pin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pinned", is(false)));

        org.junit.jupiter.api.Assertions.assertFalse(conversationRepository.findById(generalConversation.getId())
                .orElseThrow().getPinnedByUsers().contains(currentUser.getId()));
        org.junit.jupiter.api.Assertions.assertFalse(conversationRepository.findById(planningConversation.getId())
                .orElseThrow().getPinnedByUsers().contains(currentUser.getId()));
    }
}
