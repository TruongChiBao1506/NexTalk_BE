package iuh.fit.se.nextalk_be.dto;

import iuh.fit.se.nextalk_be.entity.MessageAttachment;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryMessagePayload {
    private String senderId;
    private String senderUsername;
    private String content;
    private LocalDateTime createdAt;

    @Builder.Default
    private List<MessageAttachment> attachments = new ArrayList<>();
}
