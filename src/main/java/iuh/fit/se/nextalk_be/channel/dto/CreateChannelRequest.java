package iuh.fit.se.nextalk_be.channel.dto;

import iuh.fit.se.nextalk_be.channel.ChannelType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class CreateChannelRequest {
    @NotBlank(message = "Channel name cannot be blank")
    private String name;
    private ChannelType type = ChannelType.TEXT;
    private boolean isPrivate = false;
    private Set<String> memberIds = new HashSet<>();
}
