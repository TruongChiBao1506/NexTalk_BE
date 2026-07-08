package iuh.fit.se.nextalk_be.dto.response;

import iuh.fit.se.nextalk_be.entity.QrLoginStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrLoginStatusResponse {
    private QrLoginStatus status;
    private LocalDateTime expiresAt;
    private LoginResponse login;
}
