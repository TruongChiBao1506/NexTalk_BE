package iuh.fit.se.nextalk_be.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "task_assistant_pending_actions")
@CompoundIndex(name = "assistant_user_status_expiry_idx", def = "{'userId': 1, 'status': 1, 'expiresAt': 1}")
public class TaskAssistantPendingAction extends BaseEntity {
    private String userId;
    private String conversationId;
    private String groupId;
    private String channelId;
    private String interactionId;
    private String environmentId;
    private String callId;
    private String toolName;

    @Builder.Default
    private Map<String, Object> arguments = new HashMap<>();

    private String status;
    private LocalDateTime expiresAt;
}
