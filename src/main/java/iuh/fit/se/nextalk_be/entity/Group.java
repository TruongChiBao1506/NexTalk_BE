package iuh.fit.se.nextalk_be.entity;

import iuh.fit.se.nextalk_be.entity.BaseEntity;
import iuh.fit.se.nextalk_be.entity.User;


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

    @DocumentReference(lazy = true)
    private User owner;

    private boolean requiresApproval;

    @Builder.Default
    private boolean isTaskEnabled = true;

    @org.springframework.data.mongodb.core.index.Indexed(unique = true, sparse = true)
    private String inviteCode;
}
