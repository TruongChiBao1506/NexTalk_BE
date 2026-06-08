package iuh.fit.se.nextalk_be.group;

import iuh.fit.se.nextalk_be.user.User;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "group_members")
@CompoundIndex(name = "group_user_idx", def = "{'group': 1, 'user': 1}", unique = true)
public class GroupMember {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @DocumentReference
    private Group group;

    @DocumentReference
    private User user;

    private GroupRole role;
}
