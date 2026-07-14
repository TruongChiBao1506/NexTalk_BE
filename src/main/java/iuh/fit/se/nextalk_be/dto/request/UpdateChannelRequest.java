package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.entity.ChannelType;


import lombok.Data;

import java.util.Set;

@Data
public class UpdateChannelRequest {
    private String name;
    private ChannelType type;
    private Boolean isPrivate;
    private Boolean isTaskEnabled;
    private Set<String> memberIds;
}
