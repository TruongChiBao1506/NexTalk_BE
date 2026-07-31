package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationTagResponse {
    private String id;
    private String name;
    private String color;
    private Integer position;
    private LocalDateTime createdAt;
}
