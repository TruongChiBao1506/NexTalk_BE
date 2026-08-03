package iuh.fit.se.nextalk_be.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageAttachment {
    @NotBlank(message = "Attachment URL is required")
    @Size(max = 2048, message = "Attachment URL must not exceed 2048 characters")
    private String url;

    @NotBlank(message = "Attachment type is required")
    @Size(max = 20, message = "Attachment type must not exceed 20 characters")
    private String type; // IMAGE, VIDEO, FILE

    @Size(max = 255, message = "Attachment name must not exceed 255 characters")
    private String name;

    @PositiveOrZero(message = "Attachment size must not be negative")
    private Long size;
}
