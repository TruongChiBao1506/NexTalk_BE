package iuh.fit.se.nextalk_be.chatrequest;

import iuh.fit.se.nextalk_be.common.BaseEntity;
import iuh.fit.se.nextalk_be.message.MessageAttachment;
import iuh.fit.se.nextalk_be.message.MessageType;
import iuh.fit.se.nextalk_be.user.User;
import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_requests")
@CompoundIndex(name = "chat_req_sender_receiver_status_idx", def = "{'sender': 1, 'receiver': 1, 'status': 1}")
public class ChatRequest extends BaseEntity {

    @DocumentReference
    private User sender;

    @DocumentReference
    private User receiver;

    private String message;

    private String sharedMessageId;

    private String sharedFromSenderUsername;

    private MessageType sharedMessageType;

    @Builder.Default
    private List<MessageAttachment> sharedAttachments = new ArrayList<>();

    private ChatRequestStatus status;
}
