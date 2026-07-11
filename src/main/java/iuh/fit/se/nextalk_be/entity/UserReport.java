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
    private String aiVerdict; // SAFE, WARN, BAN, HUMAN_REVIEW
    private String aiReasoning;

    // Report Status
    @Builder.Default
    private String status = "PENDING"; // PENDING, RESOLVED, DISMISSED
}
