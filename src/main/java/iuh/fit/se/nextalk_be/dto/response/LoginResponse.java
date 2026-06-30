package iuh.fit.se.nextalk_be.dto.response;

import iuh.fit.se.nextalk_be.dto.response.UserProfileResponse;


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
