package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private String id;
    private String type;
    private String content;
    private String referenceId;
    private String secondaryReferenceId;
    private boolean isRead;
    private String actionStatus;
    private LocalDateTime snoozedUntil;
    private LocalDateTime createdAt;
}
