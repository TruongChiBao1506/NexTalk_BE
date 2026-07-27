package iuh.fit.se.nextalk_be.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ImageEditResponse {
    private String sourceUrl;
    private String url;
    private String publicId;
    private String fileName;
    private String contentType;
    private Long size;
    private String model;
}
