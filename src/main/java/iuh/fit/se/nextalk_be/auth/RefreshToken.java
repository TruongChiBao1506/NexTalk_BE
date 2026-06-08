package iuh.fit.se.nextalk_be.auth;

import iuh.fit.se.nextalk_be.common.BaseEntity;
import iuh.fit.se.nextalk_be.user.User;
import lombok.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @DocumentReference
    private User user;

    @Indexed(unique = true)
    private String token;

    private LocalDateTime expiresAt;
}
