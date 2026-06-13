package iuh.fit.se.nextalk_be.group;

import iuh.fit.se.nextalk_be.common.BaseEntity;
import iuh.fit.se.nextalk_be.conversation.Conversation;
import iuh.fit.se.nextalk_be.user.User;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "groups")
public class Group extends BaseEntity {

    private String name;

    private String avatarUrl;

    @DocumentReference
    private User owner;

    @DocumentReference
    private Conversation conversation;
}
