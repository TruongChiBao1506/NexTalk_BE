package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.VoiceChannelService;

import iuh.fit.se.nextalk_be.entity.User;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceChannelServiceImpl implements VoiceChannelService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.redis.voice-ttl:120s}")
    private Duration voiceTtl;

    private static final String CHANNEL_MEMBERS_PREFIX = "voice:channel:";
    private static final String USER_CHANNEL_PREFIX = "voice:user:";

    public void joinChannel(String channelId, String userId, String groupId) {
        leaveCurrentChannel(userId);

        String channelKey = CHANNEL_MEMBERS_PREFIX + channelId;
        String userKey = USER_CHANNEL_PREFIX + userId;
        redisTemplate.opsForSet().add(channelKey, userId);
        redisTemplate.opsForValue().set(userKey, channelId + ":" + groupId);
        redisTemplate.expire(channelKey, voiceTtl);
        redisTemplate.expire(userKey, voiceTtl);
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
        String channelKey = CHANNEL_MEMBERS_PREFIX + channelId;
        removeStaleMembers(channelKey);
        Set<String> members = redisTemplate.opsForSet().members(channelKey);
        return members != null ? new ArrayList<>(members) : new ArrayList<>();
    }

    public void touchUser(String userId) {
        String userKey = USER_CHANNEL_PREFIX + userId;
        String channelInfo = redisTemplate.opsForValue().get(userKey);
        if (channelInfo == null) return;
        String[] parts = channelInfo.split(":", 2);
        if (parts.length != 2) return;
        redisTemplate.expire(userKey, voiceTtl);
        redisTemplate.expire(CHANNEL_MEMBERS_PREFIX + parts[0], voiceTtl);
    }

    @Scheduled(fixedDelayString = "${app.redis.voice-cleanup-delay:30000}")
    public void expireStaleMembers() {
        ScanOptions options = ScanOptions.scanOptions().match(CHANNEL_MEMBERS_PREFIX + "*").count(100).build();
        Cursor<String> cursor = redisTemplate.scan(options);
        if (cursor == null) return;
        try (Cursor<String> keys = cursor) {
            while (keys.hasNext()) removeStaleMembers(keys.next());
        }
    }

    private void removeStaleMembers(String channelKey) {
        Set<String> members = redisTemplate.opsForSet().members(channelKey);
        if (members == null || members.isEmpty()) return;
        for (String userId : members) {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(USER_CHANNEL_PREFIX + userId))) {
                redisTemplate.opsForSet().remove(channelKey, userId);
            }
        }
        Long remaining = redisTemplate.opsForSet().size(channelKey);
        if (remaining != null && remaining == 0) redisTemplate.delete(channelKey);
    }
}
