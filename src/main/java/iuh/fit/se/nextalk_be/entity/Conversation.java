package iuh.fit.se.nextalk_be.entity;

import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
@CompoundIndex(name = "members_updatedAt_idx", def = "{'members._id': 1, 'updatedAt': -1}")
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

    @Builder.Default
    private Set<String> hiddenByUsers = new HashSet<>();

    @Builder.Default
    private Set<String> mutedByUsers = new HashSet<>();

    private String themeColor;
    
    private String wallpaperUrl;

    @Builder.Default
    private Map<String, String> nicknames = new HashMap<>();
}
