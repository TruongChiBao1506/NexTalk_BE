package iuh.fit.se.nextalk_be.config;

import iuh.fit.se.nextalk_be.security.JwtService;


import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.RefreshTokenRepository;
import iuh.fit.se.nextalk_be.security.WebSocketSubscriptionAuthorizer;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.service.WebSocketSessionRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final WebSocketSubscriptionAuthorizer subscriptionAuthorizer;
    private final RateLimitService rateLimitService;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001}")
    private String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setUserDestinationPrefix("/user");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticate(accessor);
                    if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
                            && auth.getPrincipal() instanceof User user) {
                        rateLimitService.check("websocket:connect", user.getId(), 20, Duration.ofMinutes(1));
                    }
                    String loginSessionId = accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
                            && auth.getDetails() instanceof String value ? value : null;
                    webSocketSessionRegistry.bindLoginSession(loginSessionId, accessor.getSessionId());
                } else if (accessor != null && accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication) {
                    validateActiveSession(authentication);
                    if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                        if (!(authentication.getPrincipal() instanceof User user)) {
                            throw new org.springframework.messaging.MessageDeliveryException("Unauthorized subscription");
                        }
                        rateLimitService.check("websocket:subscribe", user.getId(), 120, Duration.ofMinutes(1));
                        subscriptionAuthorizer.authorize(user, accessor.getDestination());
                    }
                }
                return message;
            }
        });
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                webSocketSessionRegistry.registerSocket(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                webSocketSessionRegistry.unregisterSocket(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        });
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String jwt = resolveToken(accessor);
        if (jwt == null || jwt.isBlank()) {
            throw new org.springframework.messaging.MessageDeliveryException("Missing authorization token");
        }

        try {
            accessor.setUser(buildAuthentication(jwt));
        } catch (Exception e) {
            throw new org.springframework.messaging.MessageDeliveryException("Unauthorized: " + e.getMessage());
        }
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        List<String> authorization = accessor.getNativeHeader("Authorization");
        if (authorization != null && !authorization.isEmpty()) {
            String authHeader = authorization.get(0);
            if (authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }

        List<String> accessTokens = accessor.getNativeHeader("access_token");
        if (accessTokens != null && !accessTokens.isEmpty()) {
            return accessTokens.get(0);
        }

        return null;
    }

    private UsernamePasswordAuthenticationToken buildAuthentication(String jwt) {
        String userEmail = jwtService.extractUsername(jwt);
        if (userEmail == null) {
            throw new org.springframework.messaging.MessageDeliveryException("Token username is null");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
        if (!jwtService.isTokenValid(jwt, userDetails)) {
            throw new org.springframework.messaging.MessageDeliveryException("Token is invalid or expired");
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
        authentication.setDetails(jwtService.extractSessionId(jwt));
        validateActiveSession(authentication);
        return authentication;
    }

    private void validateActiveSession(UsernamePasswordAuthenticationToken authentication) {
        String sessionId = authentication.getDetails() instanceof String value ? value : null;
        if (sessionId == null) {
            return; // Legacy access tokens expire naturally within the configured 15 minutes.
        }
        if (!(authentication.getPrincipal() instanceof User user)
                || !refreshTokenRepository.existsByIdAndUserId(sessionId, user.getId())) {
            throw new org.springframework.messaging.MessageDeliveryException("Session was revoked");
        }
    }

}
