package iuh.fit.se.nextalk_be.dto.response;

import iuh.fit.se.nextalk_be.entity.ChannelType;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChannelResponse {
    private String id;
    private String name;
    private ChannelType type;
    
    @JsonProperty("isPrivate")
    private boolean isPrivate;
    
    @JsonProperty("isTaskEnabled")
    private boolean isTaskEnabled;
    @JsonProperty("isPostingRestricted")
    private boolean isPostingRestricted;
    private String groupId;
    private String conversationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
