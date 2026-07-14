package iuh.fit.se.nextalk_be.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import iuh.fit.se.nextalk_be.dto.request.DirectUploadConfirmRequest;
import iuh.fit.se.nextalk_be.dto.request.DirectUploadPrepareRequest;
import iuh.fit.se.nextalk_be.dto.response.DirectUploadPrepareResponse;
import iuh.fit.se.nextalk_be.dto.response.FileUploadResponse;
import java.io.IOException;
import java.util.Map;

public interface CloudinaryService {
    public Map uploadFile(MultipartFile file) throws IOException;
    DirectUploadPrepareResponse prepareDirectUpload(DirectUploadPrepareRequest request);
    FileUploadResponse confirmDirectUpload(DirectUploadConfirmRequest request) throws Exception;
}
