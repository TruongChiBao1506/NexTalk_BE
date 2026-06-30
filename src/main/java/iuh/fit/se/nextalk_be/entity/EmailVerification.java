package iuh.fit.se.nextalk_be.entity;

import iuh.fit.se.nextalk_be.entity.BaseEntity;
import iuh.fit.se.nextalk_be.entity.User;


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
@Document(collection = "email_verifications")
public class EmailVerification extends BaseEntity {

    @DocumentReference
    private User user;

    @Indexed(unique = true)
    private String token;

    private LocalDateTime expiresAt;

    @Builder.Default
    private boolean verified = false;
}
