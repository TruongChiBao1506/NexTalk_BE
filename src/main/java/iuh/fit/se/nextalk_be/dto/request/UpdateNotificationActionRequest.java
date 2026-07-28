package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.entity.NotificationActionStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class UpdateNotificationActionRequest {
    private NotificationActionStatus status;
    private LocalDateTime snoozedUntil;
}
