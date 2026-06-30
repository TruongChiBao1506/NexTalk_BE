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
public class Friendship extends BaseEntity {

    @DocumentReference
    private User sender;

    @DocumentReference
    private User receiver;

    private FriendshipStatus status;
}
