package iuh.fit.se.nextalk_be.entity;

import iuh.fit.se.nextalk_be.entity.BaseEntity;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.MessageAttachment;
import iuh.fit.se.nextalk_be.entity.MessageReaction;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.entity.User;


import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private LocalDateTime expiresAt;

    @Builder.Default
    private List<MessageReaction> reactions = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @Builder.Default
    private List<String> deletedByUsers = new ArrayList<>();
}
