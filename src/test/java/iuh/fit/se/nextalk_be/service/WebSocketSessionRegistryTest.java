package iuh.fit.se.nextalk_be.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketSessionRegistryTest {

    @Test
    void revokedLoginSessionClosesAllBoundSockets() throws Exception {
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
        WebSocketSession first = socket("socket-1");
        WebSocketSession second = socket("socket-2");
        registry.registerSocket(first);
        registry.registerSocket(second);
        registry.bindLoginSession("login-1", "socket-1");
        registry.bindLoginSession("login-1", "socket-2");

        registry.closeLoginSession("login-1");

        verify(first).close(org.mockito.ArgumentMatchers.any());
        verify(second).close(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unregisteredSocketIsNotClosedDuringLaterRevocation() throws Exception {
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
        WebSocketSession socket = socket("socket-1");
        registry.registerSocket(socket);
        registry.bindLoginSession("login-1", "socket-1");

        registry.unregisterSocket("socket-1");
        registry.closeLoginSession("login-1");

        verify(socket, never()).close(org.mockito.ArgumentMatchers.any());
    }

    private WebSocketSession socket(String id) {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn(id);
        when(socket.isOpen()).thenReturn(true);
        return socket;
    }
}
