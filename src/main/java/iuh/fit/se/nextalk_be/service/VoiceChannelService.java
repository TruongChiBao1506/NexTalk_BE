package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public interface VoiceChannelService {
    public void joinChannel(String channelId, String userId, String groupId);
    public String[] leaveCurrentChannel(String userId);
    public List<String> getChannelMembers(String channelId);
    public void touchUser(String userId);
    public void expireStaleMembers();
}
