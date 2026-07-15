package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.entity.ChannelType;


import lombok.Data;

import java.util.Set;

@Data
public class UpdateChannelRequest {
    private String name;
    private ChannelType type;
    @com.fasterxml.jackson.annotation.JsonProperty("isPrivate")
    private Boolean isPrivate;

    @com.fasterxml.jackson.annotation.JsonProperty("isTaskEnabled")
    private Boolean isTaskEnabled;
    @com.fasterxml.jackson.annotation.JsonProperty("isPostingRestricted")
    private Boolean isPostingRestricted;
    private Set<String> memberIds;
}
