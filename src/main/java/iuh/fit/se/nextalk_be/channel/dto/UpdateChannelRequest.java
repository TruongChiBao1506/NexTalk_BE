package iuh.fit.se.nextalk_be.channel.dto;

import iuh.fit.se.nextalk_be.channel.ChannelType;
import lombok.Data;

import java.util.Set;

@Data
public class UpdateChannelRequest {
    private String name;
    private ChannelType type;
    private Boolean isPrivate;
    private Set<String> memberIds;
}
