package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class DirectUploadPrepareRequest {
    @NotBlank
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "hash must be a SHA-256 hex value")
    private String hash;
    private String fileName;
    private String contentType;
    @PositiveOrZero
    private Long size;
}
