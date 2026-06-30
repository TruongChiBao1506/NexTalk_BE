package iuh.fit.se.nextalk_be.notification;

import iuh.fit.se.nextalk_be.entity.Notification;
import iuh.fit.se.nextalk_be.entity.NotificationType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.NotificationRepository;
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

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
// @Transactional
public class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User recipientUser;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();

        recipientUser = User.builder()
                .email("recipient@gmail.com")
                .username("recipientuser")
                .password("password123")
                .isVerified(true)
                .build();
        recipientUser = userRepository.save(recipientUser);
    }

    @Test
    @WithMockUser(username = "recipient@gmail.com")
    void getMyNotifications_EmptyList_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "recipient@gmail.com")
    void getMyNotifications_WithNotification_ReturnsList() throws Exception {
        Notification notification = Notification.builder()
                .recipient(recipientUser)
                .type(NotificationType.FRIEND_REQUEST)
                .content("alice đã gửi lời mời kết bạn")
                .referenceId(UUID.randomUUID().toString())
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        mockMvc.perform(get("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type", is("FRIEND_REQUEST")))
                .andExpect(jsonPath("$.data[0].content", is("alice đã gửi lời mời kết bạn")))
                .andExpect(jsonPath("$.data[0].read", is(false)));
    }

    @Test
    @WithMockUser(username = "recipient@gmail.com")
    void markAsRead_Success() throws Exception {
        Notification notification = Notification.builder()
                .recipient(recipientUser)
                .type(NotificationType.GROUP_INVITE)
                .content("bob đã thêm bạn vào nhóm Dev Team")
                .referenceId(UUID.randomUUID().toString())
                .isRead(false)
                .build();
        Notification saved = notificationRepository.save(notification);

        mockMvc.perform(put("/api/notifications/" + saved.getId() + "/read")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.read", is(true)));
    }

    @Test
    @WithMockUser(username = "recipient@gmail.com")
    void markAsRead_NotFound_ReturnsError() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(put("/api/notifications/" + randomId + "/read")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "recipient@gmail.com")
    void getUnreadCount_ReturnsCorrectCount() throws Exception {
        // Create 2 unread and 1 read notification
        notificationRepository.save(Notification.builder()
                .recipient(recipientUser).type(NotificationType.FRIEND_REQUEST)
                .content("Notification 1").isRead(false).build());
        notificationRepository.save(Notification.builder()
                .recipient(recipientUser).type(NotificationType.GROUP_INVITE)
                .content("Notification 2").isRead(false).build());
        notificationRepository.save(Notification.builder()
                .recipient(recipientUser).type(NotificationType.FRIEND_REQUEST)
                .content("Notification 3").isRead(true).build());

        mockMvc.perform(get("/api/notifications/unread-count")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", is(2)));
    }
}
