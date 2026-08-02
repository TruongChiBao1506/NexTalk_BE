package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkPreviewResponse {
    private Integer version;
    private String url;
    private String canonicalUrl;
    private LinkPreviewType type;
    private String provider;
    private String title;
    private String description;
    /** Kept for clients using the version 1 schema. */
    private String image;
    private String thumbnailUrl;
    private String siteName;
    private String displayDomain;
    private LinkPreviewAction action;
}
