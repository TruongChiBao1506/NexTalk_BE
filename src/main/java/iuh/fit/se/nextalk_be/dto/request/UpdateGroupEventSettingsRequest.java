package iuh.fit.se.nextalk_be.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGroupEventSettingsRequest {
    private boolean membersCanCreateEvents;
}
