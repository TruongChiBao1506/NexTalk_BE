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

    public void registerSocket(WebSocketSession session) {
        sockets.put(session.getId(), session);
    }

    public void bindLoginSession(String loginSessionId, String socketId) {
        if (loginSessionId == null || socketId == null) return;
        socketIdsByLoginSession.computeIfAbsent(loginSessionId, ignored -> ConcurrentHashMap.newKeySet()).add(socketId);
    }

    public void unregisterSocket(String socketId) {
        sockets.remove(socketId);
        socketIdsByLoginSession.values().forEach(ids -> ids.remove(socketId));
        socketIdsByLoginSession.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public void closeLoginSession(String loginSessionId) {
        Set<String> socketIds = socketIdsByLoginSession.remove(loginSessionId);
        if (socketIds == null) return;
        socketIds.forEach(socketId -> closeSocket(sockets.remove(socketId)));
    }

    public void closeLoginSessions(Iterable<String> loginSessionIds) {
        loginSessionIds.forEach(this::closeLoginSession);
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
