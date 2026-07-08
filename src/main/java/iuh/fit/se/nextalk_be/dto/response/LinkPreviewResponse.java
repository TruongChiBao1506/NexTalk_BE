package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkPreviewResponse {
    private String url;
    private String title;
    private String description;
    private String image;
    private String siteName;
}
