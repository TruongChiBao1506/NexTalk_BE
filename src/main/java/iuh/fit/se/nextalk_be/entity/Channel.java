package iuh.fit.se.nextalk_be.entity;

import iuh.fit.se.nextalk_be.entity.BaseEntity;
import iuh.fit.se.nextalk_be.entity.ChannelType;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.Group;


import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "channels")
public class Channel extends BaseEntity {

    private String name;

    @Builder.Default
    private ChannelType type = ChannelType.TEXT;

    @Builder.Default
    private boolean isPrivate = false;

    @Builder.Default
    private boolean isTaskEnabled = true;

    @DocumentReference(lazy = true)
    private Group group;

    @DocumentReference(lazy = true)
    private Conversation conversation;
}
