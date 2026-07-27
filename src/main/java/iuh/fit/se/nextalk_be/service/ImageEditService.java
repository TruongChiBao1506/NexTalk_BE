package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.request.ImageEditRequest;
import iuh.fit.se.nextalk_be.dto.response.ImageEditResponse;

public interface ImageEditService {
    ImageEditResponse edit(ImageEditRequest request);
}
