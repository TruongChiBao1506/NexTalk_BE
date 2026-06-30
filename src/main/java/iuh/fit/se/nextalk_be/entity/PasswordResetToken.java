package iuh.fit.se.nextalk_be.entity;

import iuh.fit.se.nextalk_be.entity.User;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "password_reset_tokens")
public class PasswordResetToken {
    @Id
    private String id;

    @DocumentReference(lazy = true)
    private User user;

    private String token;

    private LocalDateTime expiresAt;

    @Builder.Default
    private boolean used = false;
}
