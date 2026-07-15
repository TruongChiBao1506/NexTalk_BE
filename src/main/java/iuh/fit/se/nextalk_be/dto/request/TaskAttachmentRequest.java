package iuh.fit.se.nextalk_be.dto.request;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAttachmentRequest {
    private String id;
    private String url;
    private String name;
    private String type;
    private Long size;
}
