package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrLoginInitResponse {
    private String sessionId;
    private String qrToken;
    private LocalDateTime expiresAt;
}
