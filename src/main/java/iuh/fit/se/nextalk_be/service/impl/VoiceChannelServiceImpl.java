package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.VoiceChannelService;

import iuh.fit.se.nextalk_be.entity.User;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceChannelServiceImpl implements VoiceChannelService {

    private final StringRedisTemplate redisTemplate;

    private static final String CHANNEL_MEMBERS_PREFIX = "voice:channel:";
    private static final String USER_CHANNEL_PREFIX = "voice:user:";

    public void joinChannel(String channelId, String userId, String groupId) {
        leaveCurrentChannel(userId);

        redisTemplate.opsForSet().add(CHANNEL_MEMBERS_PREFIX + channelId, userId);
        redisTemplate.opsForValue().set(USER_CHANNEL_PREFIX + userId, channelId + ":" + groupId);
        log.info("User {} joined voice channel {} in group {}", userId, channelId, groupId);
    }

    public String[] leaveCurrentChannel(String userId) {
        String channelInfo = redisTemplate.opsForValue().get(USER_CHANNEL_PREFIX + userId);
        if (channelInfo != null) {
            String[] parts = channelInfo.split(":");
            if (parts.length >= 2) {
                String channelId = parts[0];
                String groupId = parts[1];
                redisTemplate.opsForSet().remove(CHANNEL_MEMBERS_PREFIX + channelId, userId);
                redisTemplate.delete(USER_CHANNEL_PREFIX + userId);
                log.info("User {} left voice channel {}", userId, channelId);
                return new String[]{channelId, groupId};
            }
        }
        return null;
    }

    public List<String> getChannelMembers(String channelId) {
        Set<String> members = redisTemplate.opsForSet().members(CHANNEL_MEMBERS_PREFIX + channelId);
        return members != null ? new ArrayList<>(members) : new ArrayList<>();
    }
}
