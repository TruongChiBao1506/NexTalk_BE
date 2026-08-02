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
public class MessageDeliveryDetailsResponse {
    private String messageId;
    private long seenCount;
    private long deliveredCount;
    private long sentCount;
    private long totalRecipients;
    private List<MessageDeliveryParticipantResponse> items;
    private int page;
    private int size;
    private long totalElements;
    private boolean hasMore;
}
