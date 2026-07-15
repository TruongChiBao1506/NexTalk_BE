package iuh.fit.se.nextalk_be.entity;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAttachment {
    private String id;
    private String url;
    private String name;
    private String type;
    private Long size;
}
