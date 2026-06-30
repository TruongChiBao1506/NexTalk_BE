package iuh.fit.se.nextalk_be.friend;

import iuh.fit.se.nextalk_be.dto.request.FriendshipAcceptRequest;
import iuh.fit.se.nextalk_be.dto.request.FriendshipRequest;
import iuh.fit.se.nextalk_be.entity.Friendship;
import iuh.fit.se.nextalk_be.entity.FriendshipStatus;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.FriendshipRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
// @Transactional
public class FriendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User senderUser;
    private User receiverUser;

    @BeforeEach
    void setUp() {
        friendshipRepository.deleteAll();
        userRepository.deleteAll();

        senderUser = User.builder()
                .email("sender@gmail.com")
                .username("senderuser")
                .password("password123")
                .status("ONLINE")
                .isVerified(true)
                .build();
        senderUser = userRepository.save(senderUser);

        receiverUser = User.builder()
                .email("receiver@gmail.com")
                .username("receiveruser")
                .password("password123")
                .status("ONLINE")
                .isVerified(true)
                .build();
        receiverUser = userRepository.save(receiverUser);
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void sendFriendRequest_Success() throws Exception {
        FriendshipRequest request = FriendshipRequest.builder()
                .receiverId(receiverUser.getId())
                .build();

        mockMvc.perform(post("/api/friends/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Friend request sent successfully")));
    }

    @Test
    @WithMockUser(username = "receiver@gmail.com")
    void acceptFriendRequest_Success() throws Exception {
        // First create a pending friendship
        Friendship friendship = Friendship.builder()
                .sender(senderUser)
                .receiver(receiverUser)
                .status(FriendshipStatus.PENDING)
                .build();
        friendshipRepository.save(friendship);

        FriendshipAcceptRequest request = FriendshipAcceptRequest.builder()
                .senderId(senderUser.getId())
                .build();

        mockMvc.perform(put("/api/friends/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Friend request accepted successfully")));
    }

    @Test
    @WithMockUser(username = "receiver@gmail.com")
    void rejectFriendRequest_Success() throws Exception {
        // First create a pending friendship
        Friendship friendship = Friendship.builder()
                .sender(senderUser)
                .receiver(receiverUser)
                .status(FriendshipStatus.PENDING)
                .build();
        friendshipRepository.save(friendship);

        FriendshipAcceptRequest request = FriendshipAcceptRequest.builder()
                .senderId(senderUser.getId())
                .build();

        mockMvc.perform(put("/api/friends/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Friend request rejected successfully")));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void removeFriend_Success() throws Exception {
        // First create an accepted friendship
        Friendship friendship = Friendship.builder()
                .sender(senderUser)
                .receiver(receiverUser)
                .status(FriendshipStatus.ACCEPTED)
                .build();
        friendshipRepository.save(friendship);

        mockMvc.perform(delete("/api/friends/remove")
                        .param("friendId", receiverUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Friend removed successfully")));
    }

    @Test
    @WithMockUser(username = "sender@gmail.com")
    void getFriendsList_Success() throws Exception {
        // First create an accepted friendship
        Friendship friendship = Friendship.builder()
                .sender(senderUser)
                .receiver(receiverUser)
                .status(FriendshipStatus.ACCEPTED)
                .build();
        friendshipRepository.save(friendship);

        mockMvc.perform(get("/api/friends")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].username", is("receiveruser")));
    }

    @Test
    @WithMockUser(username = "receiver@gmail.com")
    void getPendingRequests_Success() throws Exception {
        // First create a pending friendship
        Friendship friendship = Friendship.builder()
                .sender(senderUser)
                .receiver(receiverUser)
                .status(FriendshipStatus.PENDING)
                .build();
        friendshipRepository.save(friendship);

        mockMvc.perform(get("/api/friends/pending")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].username", is("senderuser")));
    }
}
