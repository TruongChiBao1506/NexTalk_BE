package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallTokenResponse {
    private String appId;
    private String token;
    private Integer uid;
    private String channelName;
}
