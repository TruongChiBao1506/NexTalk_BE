package iuh.fit.se.nextalk_be.config;

import iuh.fit.se.nextalk_be.service.impl.ScheduledMessageServiceImpl;
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

@Component
@Profile("!test")
@RequiredArgsConstructor
public class ScheduledMessageWakeupSubscriber implements MessageListener {
    private static final String WAKEUP_CHANNEL = "nextalk:scheduled-message:wakeup";

    private final RedisConnectionFactory redisConnectionFactory;
    private final ScheduledMessageServiceImpl scheduledMessageService;
    private RedisMessageListenerContainer listenerContainer;

    @PostConstruct
    public void start() {
        try {
            listenerContainer = new RedisMessageListenerContainer();
            listenerContainer.setConnectionFactory(redisConnectionFactory);
            listenerContainer.addMessageListener(this, new ChannelTopic(WAKEUP_CHANNEL));
            listenerContainer.afterPropertiesSet();
            listenerContainer.start();
        } catch (Exception ignored) {
            listenerContainer = null;
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        scheduledMessageService.onExternalWakeup();
    }

    @PreDestroy
    public void stop() {
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }
}
