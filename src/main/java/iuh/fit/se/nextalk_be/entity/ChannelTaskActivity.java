package iuh.fit.se.nextalk_be.entity;

import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "channel_task_activities")
@CompoundIndex(name = "group_channel_idx", def = "{'groupId': 1, 'channelId': 1, 'createdAt': -1}")
public class ChannelTaskActivity extends BaseEntity {

    private String groupId;
    private String channelId;
    private String taskId;

    @DocumentReference(lazy = true)
    private User actor;

    private TaskActivityType type;
    private String content;

    @Builder.Default
    private Set<String> readByUserIds = new HashSet<>();
}
