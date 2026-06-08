package iuh.fit.se.nextalk_be.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import iuh.fit.se.nextalk_be.group.dto.AddMemberRequest;
import iuh.fit.se.nextalk_be.group.dto.CreateGroupRequest;
import iuh.fit.se.nextalk_be.group.dto.UpdateGroupRequest;
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

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
public class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private User ownerUser;
    private User memberUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        ownerUser = userRepository.save(User.builder()
                .email("owner@gmail.com")
                .username("owneruser")
                .password("password123")
                .isVerified(true)
                .build());

        memberUser = userRepository.save(User.builder()
                .email("member@gmail.com")
                .username("memberuser")
                .password("password123")
                .isVerified(true)
                .build());

        otherUser = userRepository.save(User.builder()
                .email("other@gmail.com")
                .username("otheruser")
                .password("password123")
                .isVerified(true)
                .build());
    }

    @Test
    @WithMockUser(username = "owner@gmail.com")
    void createGroup_Success() throws Exception {
        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("Test Group")
                .memberIds(List.of(memberUser.getId()))
                .build();

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Test Group")))
                .andExpect(jsonPath("$.data.memberCount", is(2)))
                .andExpect(jsonPath("$.data.ownerUsername", is("owneruser")));
    }

    @Test
    @WithMockUser(username = "owner@gmail.com")
    void createGroup_WithoutMembers_Success() throws Exception {
        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("Solo Group")
                .build();

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberCount", is(1)));
    }

    @Test
    @WithMockUser(username = "owner@gmail.com")
    void createGroup_BlankName_Fails() throws Exception {
        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("")
                .build();

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "owner@gmail.com")
    void getGroupById_Success() throws Exception {
        CreateGroupRequest createRequest = CreateGroupRequest.builder()
                .name("Test Group")
                .memberIds(List.of(memberUser.getId()))
                .build();

        String responseBody = mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn().getResponse().getContentAsString();

        String groupId = objectMapper.readTree(responseBody).at("/data/id").asText();

        mockMvc.perform(get("/api/groups/" + groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Test Group")));
    }

    @Test
    @WithMockUser(username = "owner@gmail.com")
    void getGroupById_NotFound() throws Exception {
        mockMvc.perform(get("/api/groups/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @WithMockUser(username = "owner@gmail.com")
    void updateGroup_Success() throws Exception {
        CreateGroupRequest createRequest = CreateGroupRequest.builder()
                .name("Old Name")
                .build();

        String createBody = mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn().getResponse().getContentAsString();

        String groupId = objectMapper.readTree(createBody).at("/data/id").asText();

        UpdateGroupRequest updateRequest = UpdateGroupRequest.builder()
                .name("New Name")
                .build();

        mockMvc.perform(put("/api/groups/" + groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("New Name")));
    }

    @Test
    @WithMockUser(username = "owner@gmail.com")
    void addMember_Success() throws Exception {
        CreateGroupRequest createRequest = CreateGroupRequest.builder()
                .name("Group")
                .build();

        String createBody = mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn().getResponse().getContentAsString();

        String groupId = objectMapper.readTree(createBody).at("/data/id").asText();

        AddMemberRequest addMemberRequest = AddMemberRequest.builder()
                .userId(otherUser.getId())
                .build();

        mockMvc.perform(post("/api/groups/" + groupId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addMemberRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberCount", is(2)));
    }

    @Test
    @WithMockUser(username = "owner@gmail.com")
    void removeMember_Success() throws Exception {
        CreateGroupRequest createRequest = CreateGroupRequest.builder()
                .name("Group")
                .memberIds(List.of(memberUser.getId()))
                .build();

        String createBody = mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn().getResponse().getContentAsString();

        String groupId = objectMapper.readTree(createBody).at("/data/id").asText();

        mockMvc.perform(delete("/api/groups/" + groupId + "/members/" + memberUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @WithMockUser(username = "owner@gmail.com")
    void deleteGroup_Success() throws Exception {
        CreateGroupRequest createRequest = CreateGroupRequest.builder()
                .name("To Delete")
                .build();

        String createBody = mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn().getResponse().getContentAsString();

        String groupId = objectMapper.readTree(createBody).at("/data/id").asText();

        mockMvc.perform(delete("/api/groups/" + groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @WithMockUser(username = "owner@gmail.com")
    void getMyGroups_Success() throws Exception {
        CreateGroupRequest r1 = CreateGroupRequest.builder().name("Group 1").build();
        CreateGroupRequest r2 = CreateGroupRequest.builder().name("Group 2").build();

        mockMvc.perform(post("/api/groups").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(r1))).andReturn();
        mockMvc.perform(post("/api/groups").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(r2))).andReturn();

        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }
}
