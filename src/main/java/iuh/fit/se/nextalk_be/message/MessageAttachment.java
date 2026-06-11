package iuh.fit.se.nextalk_be.message;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageAttachment {
    private String url;
    private String type; // IMAGE, VIDEO, FILE
    private String name;
}
