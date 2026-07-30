package iuh.fit.se.nextalk_be.config;

import iuh.fit.se.nextalk_be.service.SessionRevocationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class SessionRevocationSubscriber implements MessageListener {
    private final RedisConnectionFactory redisConnectionFactory;
    private final SessionRevocationService sessionRevocationService;
    private RedisMessageListenerContainer listenerContainer;

    @PostConstruct
    public void start() {
        try {
            listenerContainer = new RedisMessageListenerContainer();
            listenerContainer.setConnectionFactory(redisConnectionFactory);
            listenerContainer.addMessageListener(
                    this,
                    new ChannelTopic(SessionRevocationService.REVOCATION_CHANNEL));
            listenerContainer.afterPropertiesSet();
            listenerContainer.start();
        } catch (Exception ignored) {
            listenerContainer = null;
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        sessionRevocationService.closeSessionsFromRemoteEvent(
                new String(message.getBody(), StandardCharsets.UTF_8));
    }

    @PreDestroy
    public void stop() {
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }
}
