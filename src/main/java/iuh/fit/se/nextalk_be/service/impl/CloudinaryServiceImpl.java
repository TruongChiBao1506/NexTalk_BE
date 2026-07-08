package iuh.fit.se.nextalk_be.service.impl;
import iuh.fit.se.nextalk_be.service.CloudinaryService;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {

    private final Cloudinary cloudinary;

    public Map uploadFile(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        String resourceType = "auto";
        if (contentType != null && (contentType.startsWith("audio/") || contentType.startsWith("video/"))) {
            resourceType = "video";
        }

        return cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap("resource_type", resourceType)
        );
    }
}
