package iuh.fit.se.nextalk_be.entity;

import lombok.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "qr_login_sessions")
public class QrLoginSession extends BaseEntity {

    @Indexed(unique = true)
    private String sessionId;

    @Indexed(unique = true)
    private String qrToken;

    @Builder.Default
    private QrLoginStatus status = QrLoginStatus.PENDING;

    @DocumentReference
    private User user;

    private LocalDateTime expiresAt;

    private LocalDateTime confirmedAt;

    private LocalDateTime consumedAt;

    private String ipAddress;

    private String userAgent;
}
