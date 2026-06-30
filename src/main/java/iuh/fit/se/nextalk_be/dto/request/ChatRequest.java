package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.dto.request.ChatRequestStatus;
import iuh.fit.se.nextalk_be.entity.BaseEntity;
import iuh.fit.se.nextalk_be.entity.MessageAttachment;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.entity.User;


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
