package iuh.fit.se.nextalk_be.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockStatusResponse {
    private String userId;
    private boolean blockedByMe;
    private boolean blockedMe;
    private boolean blocked;
}
