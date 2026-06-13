package iuh.fit.se.nextalk_be.conversation;

import iuh.fit.se.nextalk_be.common.BaseEntity;
import iuh.fit.se.nextalk_be.user.User;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
public class Conversation extends BaseEntity {

    private ConversationType type;

    private String name;

    @DocumentReference(lazy = true)
    private User owner;

    @DocumentReference(lazy = true)
    @Builder.Default
    private Set<User> members = new HashSet<>();

    @Builder.Default
    private int selfDestructSeconds = 0;

    @Builder.Default
    private Set<String> pinnedByUsers = new HashSet<>();

    @Builder.Default
    private Set<String> deletedByUsers = new HashSet<>();
}
