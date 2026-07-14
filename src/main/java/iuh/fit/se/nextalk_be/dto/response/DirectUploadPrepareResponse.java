package iuh.fit.se.nextalk_be.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DirectUploadPrepareResponse {
    private boolean deduplicated;
    private FileUploadResponse file;
    private String cloudName;
    private String apiKey;
    private Long timestamp;
    private String signature;
    private String publicId;
    private String resourceType;
    private String uploadUrl;
}
