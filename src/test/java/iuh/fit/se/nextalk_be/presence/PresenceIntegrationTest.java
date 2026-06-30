package iuh.fit.se.nextalk_be.presence;

import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.PresenceService;


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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
// @Transactional
public class PresenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PresenceService presenceService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        testUser = User.builder()
                .email("testuser@gmail.com")
                .username("testuser")
                .password("password123")
                .status("OFFLINE")
                .isVerified(true)
                .build();
        testUser = userRepository.save(testUser);
    }

    @Test
    @WithMockUser(username = "testuser@gmail.com")
    void updatePresenceStatus_Success() throws Exception {
        mockMvc.perform(put("/api/users/presence/status")
                        .param("status", "AWAY")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("AWAY")));

        // Verify status retrieved from presenceService is AWAY
        String status = presenceService.getUserStatus(testUser.getId());
        assert "AWAY".equalsIgnoreCase(status);
    }

    @Test
    @WithMockUser(username = "testuser@gmail.com")
    void updatePresenceStatus_InvalidStatus_ReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/users/presence/status")
                        .param("status", "INVALID_STATUS")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @WithMockUser(username = "testuser@gmail.com")
    void getMyProfile_IncludesPresenceDetails() throws Exception {
        // Pre-set in presenceService
        presenceService.setUserStatus(testUser.getId(), "ONLINE");

        mockMvc.perform(get("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("ONLINE")));
    }

    @Test
    @WithMockUser(username = "testuser@gmail.com")
    void markOffline_SetsLastSeen() throws Exception {
        mockMvc.perform(put("/api/users/presence/status")
                        .param("status", "OFFLINE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("OFFLINE")))
                .andExpect(jsonPath("$.data.lastSeen", notNullValue()));

        assert presenceService.getUserLastSeen(testUser.getId()) != null;
    }
}
