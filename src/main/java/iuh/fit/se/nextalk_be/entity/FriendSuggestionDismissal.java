package iuh.fit.se.nextalk_be.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "friend_suggestion_dismissals")
@CompoundIndex(
        name = "friend_suggestion_dismissal_user_candidate_idx",
        def = "{'userId': 1, 'candidateUserId': 1}",
        unique = true)
public class FriendSuggestionDismissal {

    @Id
    private String id;

    private String userId;

    private String candidateUserId;

    @Indexed(expireAfter = "0s")
    private LocalDateTime expiresAt;
}
