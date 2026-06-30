package iuh.fit.se.nextalk_be.dto.request;

import iuh.fit.se.nextalk_be.dto.response.UserProfileResponse;


import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestResponse {
    private String id;
    private UserProfileResponse sender;
    private UserProfileResponse receiver;
    private String message;
    private String status;
    private String conversationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
