package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {
    private String id;
    private String email;
    private String username;
    @com.fasterxml.jackson.annotation.JsonProperty("isVerified")
    private boolean isVerified;
}
