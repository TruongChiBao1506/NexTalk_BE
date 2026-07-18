package iuh.fit.se.nextalk_be.security;

import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.ChannelRepository;
import iuh.fit.se.nextalk_be.repository.GroupMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class WebSocketSubscriptionAuthorizer {
    private static final Pattern GROUP_VOICE_TOPIC = Pattern.compile("^/topic/group\\.([A-Za-z0-9_-]+)\\.voice$");
    private static final Pattern CHANNEL_ACTIVITY_TOPIC = Pattern.compile("^/topic/channel\\.([A-Za-z0-9_-]+)\\.task-activities$");

    private final GroupMemberRepository groupMemberRepository;
    private final ChannelRepository channelRepository;

    public void authorize(User user, String destination) {
        if (user == null || destination == null) deny();
        if (destination.startsWith("/user/") || "/topic/presence".equals(destination)) return;

        Matcher groupMatcher = GROUP_VOICE_TOPIC.matcher(destination);
        if (groupMatcher.matches()
                && groupMemberRepository.existsByGroupIdAndUserId(groupMatcher.group(1), user.getId())) return;

        Matcher channelMatcher = CHANNEL_ACTIVITY_TOPIC.matcher(destination);
        if (channelMatcher.matches() && canAccessChannel(channelMatcher.group(1), user.getId())) return;

        deny();
    }

    private boolean canAccessChannel(String channelId, String userId) {
        return channelRepository.findById(channelId).map(channel -> {
            if (channel.getGroup() == null
                    || !groupMemberRepository.existsByGroupIdAndUserId(channel.getGroup().getId(), userId)) return false;
            if (!channel.isPrivate()) return true;
            return channel.getConversation() != null
                    && channel.getConversation().getMembers() != null
                    && channel.getConversation().getMembers().stream()
                    .anyMatch(member -> userId.equals(member.getId()));
        }).orElse(false);
    }

    private void deny() {
        throw new MessageDeliveryException("Subscription destination is not allowed");
    }
}
