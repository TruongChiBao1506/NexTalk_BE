package iuh.fit.se.nextalk_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {
    private String url;
    private String publicId;
    private String fileName;
    private String contentType;
    private Long size;
}
