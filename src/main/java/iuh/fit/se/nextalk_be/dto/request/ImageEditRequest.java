package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImageEditRequest {
    @NotBlank(message = "Message ID is required")
    private String messageId;

    @NotBlank(message = "Source image URL is required")
    private String sourceUrl;

    @NotNull(message = "Edit operation is required")
    private ImageEditOperation operation;

    @Size(max = 200, message = "Subject must not exceed 200 characters")
    private String subject;

    @Size(max = 200, message = "Replacement must not exceed 200 characters")
    private String replacement;

    @Size(max = 16, message = "Color must not exceed 16 characters")
    private String color;

    @Size(max = 500, message = "Prompt must not exceed 500 characters")
    private String prompt;

    @Size(max = 8, message = "Aspect ratio must not exceed 8 characters")
    private String aspectRatio;
}
