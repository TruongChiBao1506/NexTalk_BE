package iuh.fit.se.nextalk_be.service;

import java.util.List;

public interface VoiceChannelService {
    public void joinChannel(String channelId, String userId, String groupId);
    public void joinChannel(String channelId, String userId, String groupId, String sessionId);
    public String[] leaveCurrentChannel(String userId);
    public String[] leaveChannelBySessionId(String sessionId);
    public List<String> getChannelMembers(String channelId);
    public void touchUser(String userId);
    public void expireStaleMembers();
}
