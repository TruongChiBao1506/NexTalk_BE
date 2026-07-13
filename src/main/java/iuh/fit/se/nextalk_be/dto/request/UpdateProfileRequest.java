package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")

    private String username;

    private String avatarUrl;

    private String bio;

    /** Birthday as YYYY-MM-dd */
    private String birthday;

    private Boolean enableBirthdayNotification;

    private Boolean showActivityStatus;

    private Boolean blockStrangerMessages;
}
