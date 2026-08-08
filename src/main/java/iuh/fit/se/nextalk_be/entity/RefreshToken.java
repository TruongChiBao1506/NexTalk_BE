package iuh.fit.se.nextalk_be.entity;

import iuh.fit.se.nextalk_be.entity.BaseEntity;
import iuh.fit.se.nextalk_be.entity.User;


import lombok.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Indexed
    private String familyId;

    @Builder.Default
    private List<String> usedTokenDigests = new ArrayList<>();

    private String previousTokenDigest;

    @Indexed(expireAfter = "0s")
    private LocalDateTime expiresAt;

    private String userAgent;

    private String ipAddress;

    private LocalDateTime lastUsedAt;

    private String fcmToken;
}
