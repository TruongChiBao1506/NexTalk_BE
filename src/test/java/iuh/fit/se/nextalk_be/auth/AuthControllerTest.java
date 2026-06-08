package iuh.fit.se.nextalk_be.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.nextalk_be.auth.dto.LoginRequest;
import iuh.fit.se.nextalk_be.auth.dto.RegisterRequest;
import iuh.fit.se.nextalk_be.auth.dto.TokenRefreshRequest;
import iuh.fit.se.nextalk_be.user.User;
import iuh.fit.se.nextalk_be.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        emailVerificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void register_Success() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("newuser@gmail.com")
                .username("newuser")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is("newuser@gmail.com")))
                .andExpect(jsonPath("$.data.username", is("newuser")))
                .andExpect(jsonPath("$.data.isVerified", is(false)));
    }

    @Test
    void register_InvalidEmail_BadRequest() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("invalid-email")
                .username("newuser")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Validation failed")));
    }

    @Test
    void verifyEmail_Success() throws Exception {
        User user = User.builder()
                .email("verify@gmail.com")
                .username("verifyuser")
                .password(passwordEncoder.encode("password123"))
                .status("OFFLINE")
                .isVerified(false)
                .build();
        user = userRepository.save(user);

        String token = UUID.randomUUID().toString();
        EmailVerification verification = EmailVerification.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .verified(false)
                .build();
        emailVerificationRepository.save(verification);

        mockMvc.perform(get("/api/auth/verify-email")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Email verified successfully. You can now log in.")));

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assert(updatedUser.isVerified());
    }

    @Test
    void login_Success() throws Exception {
        User user = User.builder()
                .email("login@gmail.com")
                .username("loginuser")
                .password(passwordEncoder.encode("password123"))
                .status("OFFLINE")
                .isVerified(true)
                .build();
        userRepository.save(user);

        LoginRequest request = new LoginRequest("login@gmail.com", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.user.email", is("login@gmail.com")));
    }

    @Test
    void login_UnverifiedUser_BadRequest() throws Exception {
        User user = User.builder()
                .email("unverified@gmail.com")
                .username("unverifieduser")
                .password(passwordEncoder.encode("password123"))
                .status("OFFLINE")
                .isVerified(false)
                .build();
        userRepository.save(user);

        LoginRequest request = new LoginRequest("unverified@gmail.com", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Account not verified. Please verify your email first.")));
    }
}
