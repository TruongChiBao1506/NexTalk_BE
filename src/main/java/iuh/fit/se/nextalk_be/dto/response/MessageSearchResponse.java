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
public class MessageSearchResponse {
    private List<MessageResponse> items;
    private int page;
    private int size;
    private long totalElements;
    private boolean hasMore;
}
