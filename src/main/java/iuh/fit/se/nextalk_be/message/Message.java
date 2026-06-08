package iuh.fit.se.nextalk_be.message;

import iuh.fit.se.nextalk_be.common.BaseEntity;
import iuh.fit.se.nextalk_be.conversation.Conversation;
import iuh.fit.se.nextalk_be.user.User;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
public class Message extends BaseEntity {

    @DocumentReference
    private Conversation conversation;

    @DocumentReference
    private User sender;

    private String content;

    private MessageType messageType;

    private UUID parentId;

    @Builder.Default
    private boolean isEdited = false;

    private LocalDateTime editedAt;

    @Builder.Default
    private boolean isRecalled = false;

    @Builder.Default
    private boolean isPinned = false;

    @Builder.Default
    private List<MessageReaction> reactions = new ArrayList<>();

    @Builder.Default
    private List<UUID> deletedByUsers = new ArrayList<>();
}
