package iuh.fit.se.nextalk_be.notification.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private UUID id;
    private String type;
    private String content;
    private String referenceId;
    private boolean isRead;
    private LocalDateTime createdAt;
}
