package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAttachmentResponse {
    private String id;
    private String url;
    private String name;
    private String type;
    private Long size;
}
