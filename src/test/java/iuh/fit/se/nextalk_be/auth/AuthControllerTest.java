package iuh.fit.se.nextalk_be.auth;

import iuh.fit.se.nextalk_be.dto.request.LoginRequest;
import iuh.fit.se.nextalk_be.dto.request.RegisterRequest;
import iuh.fit.se.nextalk_be.dto.request.TokenRefreshRequest;
import iuh.fit.se.nextalk_be.entity.EmailVerification;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.EmailVerificationRepository;
import iuh.fit.se.nextalk_be.repository.RefreshTokenRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.security.SecureTokenService;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.net.HttpCookie;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// @Transactional
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

    @Autowired
    private SecureTokenService secureTokenService;

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
                .token(secureTokenService.digest(token))
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

    @Test
    void protectedEndpointWithoutAuthentication_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void mobileRefresh_DoesNotFallBackToCookieToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Client-Platform", "mobile")
                        .cookie(new Cookie("nextalk_refresh", "stale-native-cookie")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Refresh token is required")));
    }

    @Test
    void mobileRefresh_WithBodyToken_ReturnsRotatedTokens() throws Exception {
        User user = User.builder()
                .email("refresh@gmail.com")
                .username("refreshuser")
                .password(passwordEncoder.encode("password123"))
                .status("OFFLINE")
                .isVerified(true)
                .build();
        userRepository.save(user);

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .header("X-Client-Platform", "mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("refresh@gmail.com", "password123"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String refreshToken = loginJson.path("data").path("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Client-Platform", "mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                TokenRefreshRequest.builder().refreshToken(refreshToken).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()));
    }

    @Test
    void mobileRefresh_ConcurrentRetryReturnsAccessWithoutInvalidatingSession() throws Exception {
        User user = User.builder()
                .email("concurrent-refresh@gmail.com")
                .username("concurrentrefresh")
                .password(passwordEncoder.encode("password123"))
                .status("OFFLINE")
                .isVerified(true)
                .build();
        userRepository.save(user);

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .header("X-Client-Platform", "mobile")
                        .header("User-Agent", "Concurrent Mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("concurrent-refresh@gmail.com", "password123"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String originalRefreshToken = objectMapper.readTree(loginResponse)
                .path("data").path("refreshToken").asText();
        String refreshBody = objectMapper.writeValueAsString(
                TokenRefreshRequest.builder().refreshToken(originalRefreshToken).build());

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Client-Platform", "mobile")
                        .header("User-Agent", "Concurrent Mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()));

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Client-Platform", "mobile")
                        .header("User-Agent", "Concurrent Mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", nullValue()));
    }

    @Test
    void webRefresh_ConcurrentRetryKeepsTheRotatedCookie() throws Exception {
        User user = User.builder()
                .email("web-concurrent-refresh@gmail.com")
                .username("webconcurrentrefresh")
                .password(passwordEncoder.encode("password123"))
                .status("OFFLINE")
                .isVerified(true)
                .build();
        userRepository.save(user);

        String loginCookieHeader = mockMvc.perform(post("/api/auth/login")
                        .header("X-Client-Platform", "web")
                        .header("User-Agent", "Concurrent Web")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("web-concurrent-refresh@gmail.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.SET_COOKIE);
        String originalRefreshToken = HttpCookie.parse(loginCookieHeader).get(0).getValue();
        Cookie originalCookie = new Cookie("nextalk_refresh", originalRefreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Client-Platform", "web")
                        .header("Origin", "http://localhost:3000")
                        .header("User-Agent", "Concurrent Web")
                        .cookie(originalCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Client-Platform", "web")
                        .header("Origin", "http://localhost:3000")
                        .header("User-Agent", "Concurrent Web")
                        .cookie(originalCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()));
    }
}
