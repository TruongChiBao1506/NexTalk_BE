package iuh.fit.se.nextalk_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSyncResponse {
    private List<MessageResponse> messages;
    private List<String> deletedMessageIds;
    private LocalDateTime cursor;
    private boolean fullSnapshot;
}
