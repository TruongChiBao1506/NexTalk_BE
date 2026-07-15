package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.entity.Channel;
import iuh.fit.se.nextalk_be.entity.ChannelType;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class CreateChannelRequest {
    @NotBlank(message = "Channel name cannot be blank")
    private String name;
    private ChannelType type = ChannelType.TEXT;
    @com.fasterxml.jackson.annotation.JsonProperty("isPrivate")
    private boolean isPrivate = false;

    @com.fasterxml.jackson.annotation.JsonProperty("isTaskEnabled")
    private boolean isTaskEnabled = false;
    @com.fasterxml.jackson.annotation.JsonProperty("isPostingRestricted")
    private boolean isPostingRestricted = false;
    private Set<String> memberIds = new HashSet<>();
}
