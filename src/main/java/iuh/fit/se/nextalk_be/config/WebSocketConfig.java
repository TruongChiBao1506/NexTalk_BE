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
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

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
                }
                return message;
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

        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }
}
