package iuh.fit.se.nextalk_be.auth.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {
    private UUID id;
    private String email;
    private String username;
    @com.fasterxml.jackson.annotation.JsonProperty("isVerified")
    private boolean isVerified;
}
