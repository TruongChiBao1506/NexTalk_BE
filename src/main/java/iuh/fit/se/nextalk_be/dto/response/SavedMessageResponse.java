package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedMessageResponse {
    private String id;
    private LocalDateTime savedAt;
    private String conversationName;
    private MessageResponse message;
}
