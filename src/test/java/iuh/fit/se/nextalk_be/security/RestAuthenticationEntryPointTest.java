package iuh.fit.se.nextalk_be.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestAuthenticationEntryPointTest {
    @Test
    void unauthenticatedRequestReturnsJson401WithBearerChallenge() throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                new MockHttpServletRequest("GET", "/api/groups/invites/pending"),
                response,
                new InsufficientAuthenticationException("test"));

        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
        assertTrue(response.getContentAsString().contains("Authentication required"));
    }
}
