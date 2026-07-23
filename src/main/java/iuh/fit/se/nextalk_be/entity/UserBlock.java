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
@Document(collection = "user_blocks")
@CompoundIndex(name = "blocker_blocked_idx", def = "{'blocker': 1, 'blocked': 1}", unique = true)
public class UserBlock extends BaseEntity {

    @DocumentReference
    private User blocker;

    @DocumentReference
    private User blocked;
}
