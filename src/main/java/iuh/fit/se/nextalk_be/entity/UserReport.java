package iuh.fit.se.nextalk_be.entity;

import lombok.*;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_reports")
public class UserReport extends BaseEntity {

    @DBRef
    private User reporter;

    @DBRef
    private User reportedUser;

    private String conversationId;

    private String reason;
    private String description;

    // AI Fields
    private String aiVerdict; // SAFE, WARN, HUMAN_REVIEW, ERROR (advisory only)
    private String aiReasoning;

    // Report Status
    @Builder.Default
    private String status = "PENDING"; // PENDING, PENDING_REVIEW, RESOLVED, DISMISSED

    private ModerationDecision finalDecision;

    @DBRef
    private User resolvedBy;

    private java.time.LocalDateTime resolvedAt;
}
