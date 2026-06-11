package iuh.fit.se.nextalk_be.message;

import iuh.fit.se.nextalk_be.common.BaseEntity;
import iuh.fit.se.nextalk_be.conversation.Conversation;
import iuh.fit.se.nextalk_be.user.User;
import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
@CompoundIndex(name = "msg_conv_created_idx", def = "{'conversation': 1, 'createdAt': -1}")
public class Message extends BaseEntity {

    @DocumentReference(lazy = true)
    private Conversation conversation;

    @DocumentReference(lazy = true)
    private User sender;

    private String content;

    private MessageType messageType;

    @Builder.Default
    private List<MessageAttachment> attachments = new ArrayList<>();

    private String parentId;

    private String forwardedFromMessageId;

    private String forwardedFromSenderUsername;

    @Builder.Default
    private boolean isEdited = false;

    private LocalDateTime editedAt;

    @Builder.Default
    private boolean isRecalled = false;

    @Builder.Default
    private boolean isPinned = false;

    private LocalDateTime pinnedAt;

    @Builder.Default
    private List<MessageReaction> reactions = new ArrayList<>();

    @Builder.Default
    private List<String> deletedByUsers = new ArrayList<>();
}
