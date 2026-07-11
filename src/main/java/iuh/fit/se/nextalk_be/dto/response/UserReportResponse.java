package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReportResponse {
    private String id;
    private String reporterId;
    private String reportedUserId;
    private String conversationId;
    private String reason;
    private String description;
    private String status;
    private String aiVerdict;
    private String aiReasoning;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
