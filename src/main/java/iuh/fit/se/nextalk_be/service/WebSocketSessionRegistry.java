package iuh.fit.se.nextalk_be.service;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {
    private final ConcurrentHashMap<String, WebSocketSession> sockets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> socketIdsByLoginSession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> loginSessionBySocketId = new ConcurrentHashMap<>();

    public void registerSocket(WebSocketSession session) {
        sockets.put(session.getId(), session);
    }

    public void bindLoginSession(String loginSessionId, String socketId) {
        if (loginSessionId == null || socketId == null) return;
        String previousLoginSessionId = loginSessionBySocketId.put(socketId, loginSessionId);
        if (previousLoginSessionId != null && !previousLoginSessionId.equals(loginSessionId)) {
            removeSocketFromLoginSession(previousLoginSessionId, socketId);
        }
        socketIdsByLoginSession.computeIfAbsent(loginSessionId, ignored -> ConcurrentHashMap.newKeySet()).add(socketId);
    }

    public void unregisterSocket(String socketId) {
        sockets.remove(socketId);
        String loginSessionId = loginSessionBySocketId.remove(socketId);
        if (loginSessionId != null) {
            removeSocketFromLoginSession(loginSessionId, socketId);
        }
    }

    public void closeLoginSession(String loginSessionId) {
        Set<String> socketIds = socketIdsByLoginSession.remove(loginSessionId);
        if (socketIds == null) return;
        socketIds.forEach(socketId -> {
            loginSessionBySocketId.remove(socketId, loginSessionId);
            closeSocket(sockets.remove(socketId));
        });
    }

    public void closeLoginSessions(Iterable<String> loginSessionIds) {
        loginSessionIds.forEach(this::closeLoginSession);
    }

    private void removeSocketFromLoginSession(String loginSessionId, String socketId) {
        socketIdsByLoginSession.computeIfPresent(loginSessionId, (ignored, socketIds) -> {
            socketIds.remove(socketId);
            return socketIds.isEmpty() ? null : socketIds;
        });
    }

    private void closeSocket(WebSocketSession socket) {
        if (socket == null || !socket.isOpen()) return;
        try {
            socket.close(CloseStatus.POLICY_VIOLATION.withReason("Login session revoked"));
        } catch (IOException ignored) {
            // The socket is already unusable; registry cleanup is sufficient.
        }
    }
}
