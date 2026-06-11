package iuh.fit.se.nextalk_be.summary.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryMessagePayload {
    private String senderId;
    private String senderUsername;
    private String content;
    private LocalDateTime createdAt;
}
