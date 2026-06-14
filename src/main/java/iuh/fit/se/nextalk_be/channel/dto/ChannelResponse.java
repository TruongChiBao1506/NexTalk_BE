package iuh.fit.se.nextalk_be.channel.dto;

import iuh.fit.se.nextalk_be.channel.ChannelType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChannelResponse {
    private String id;
    private String name;
    private ChannelType type;
    private boolean isPrivate;
    private String groupId;
    private String conversationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
