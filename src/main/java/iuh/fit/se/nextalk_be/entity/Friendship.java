package iuh.fit.se.nextalk_be.entity;

import iuh.fit.se.nextalk_be.entity.BaseEntity;
import iuh.fit.se.nextalk_be.entity.FriendshipStatus;
import iuh.fit.se.nextalk_be.entity.User;


import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "friendships")
@CompoundIndex(name = "sender_receiver_idx", def = "{'sender': 1, 'receiver': 1}", unique = true)
@CompoundIndex(name = "friend_receiver_status_idx", def = "{'receiver._id': 1, 'status': 1}")
@CompoundIndex(name = "friend_sender_status_idx", def = "{'sender._id': 1, 'status': 1}")
public class Friendship extends BaseEntity {

    @DocumentReference
    private User sender;

    @DocumentReference
    private User receiver;

    private FriendshipStatus status;
}
