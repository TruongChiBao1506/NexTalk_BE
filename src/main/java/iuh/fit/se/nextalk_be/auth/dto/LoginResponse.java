package iuh.fit.se.nextalk_be.auth.dto;

import iuh.fit.se.nextalk_be.user.dto.UserProfileResponse;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private UserProfileResponse user;
}
