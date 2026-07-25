package iuh.fit.se.nextalk_be.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.server.HandshakeFailureException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/nextalk-websocket-test",
        "spring.mongodb.uri=mongodb://localhost:27017/nextalk-websocket-test",
        "spring.data.mongodb.auto-index-creation=false",
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "app.jwt.secret=nextalk-test-secret-nextalk-test-secret-nextalk-test-secret",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-key",
        "cloudinary.api-secret=test-secret"
})
@AutoConfigureMockMvc
class WebSocketHandshakeSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void nativeWebSocketHandshakeIsNotRejectedByHttpSecurity() throws Exception {
        // MockMvc has no real JSR-356 servlet container, so a request that gets
        // through Security must fail inside Spring's protocol upgrade handler.
        // A missing permit rule would instead return HTTP 401/403 before this.
        ServletException exception = assertThrows(ServletException.class, () ->
                mockMvc.perform(get("/ws-raw")
                                .header("Connection", "Upgrade")
                                .header("Upgrade", "websocket")
                                .header("Sec-WebSocket-Version", "13")
                                .header("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ=="))
                        .andReturn()
        );

        assertInstanceOf(HandshakeFailureException.class, exception.getCause());
    }
}
