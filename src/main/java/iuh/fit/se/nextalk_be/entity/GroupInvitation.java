package iuh.fit.se.nextalk_be.entity;

import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "group_invitations")
@CompoundIndex(name = "group_invitee_idx", def = "{'group': 1, 'invitee': 1}", unique = true)
public class GroupInvitation extends BaseEntity {

    @DocumentReference
    private Group group;

    @DocumentReference
    private User inviter;

    @DocumentReference
    private User invitee;

    private InvitationStatus status;
}
