package iuh.fit.se.nextalk_be.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceChannelEvent {
    private String type; // "JOIN" or "LEAVE"
    private String channelId;
    private String groupId;
    private String userId;
    private List<String> currentMembers;
}
