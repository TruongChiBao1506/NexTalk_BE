package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateNicknameRequest {
    @Size(max = 40, message = "Nickname must not exceed 40 characters")
    private String nickname;
}
