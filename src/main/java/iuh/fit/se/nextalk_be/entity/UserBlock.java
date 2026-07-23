package iuh.fit.se.nextalk_be.entity;

import iuh.fit.se.nextalk_be.entity.BaseEntity;
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
@Document(collection = "user_blocks")
@CompoundIndex(name = "blocker_blocked_idx", def = "{'blocker': 1, 'blocked': 1}", unique = true)
@CompoundIndex(name = "blocker_blocked_v2_idx", def = "{'blocker._id': 1, 'blocked._id': 1}")
@CompoundIndex(name = "blocked_blocker_v2_idx", def = "{'blocked._id': 1, 'blocker._id': 1}")
public class UserBlock extends BaseEntity {

    @DocumentReference
    private User blocker;

    @DocumentReference
    private User blocked;
}
