package iuh.fit.se.nextalk_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class DirectUploadConfirmRequest {
    @NotBlank
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "hash must be a SHA-256 hex value")
    private String hash;
    @NotBlank
    private String publicId;
    @NotBlank
    private String resourceType;
    @NotBlank
    private String url;
    @NotBlank
    private String responseSignature;
    @NotBlank
    private String version;
    private String format;
    @PositiveOrZero
    private Long bytes;
    private String fileName;
    private String contentType;
    @PositiveOrZero
    private Long size;
}
