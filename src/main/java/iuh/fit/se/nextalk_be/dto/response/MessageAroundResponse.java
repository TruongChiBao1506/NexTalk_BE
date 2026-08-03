package iuh.fit.se.nextalk_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageAroundResponse {
    private List<MessageResponse> items;
    private String anchorMessageId;
    private String nextCursor;
    private boolean hasMore;
    private boolean hasNewer;
}
