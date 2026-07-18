package iuh.fit.se.nextalk_be.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Document(collection = "media_assets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaAsset {

    /** SHA-256 of the exact uploaded bytes. */
    @Id
    private String hash;

    private String url;
    private String publicId;
    private String resourceType;
    private String format;
    private Long size;
    private String contentType;
    private LocalDateTime createdAt;

    @Builder.Default
    private Set<String> allowedUserIds = new HashSet<>();

    @Builder.Default
    private Set<String> conversationIds = new HashSet<>();
}
