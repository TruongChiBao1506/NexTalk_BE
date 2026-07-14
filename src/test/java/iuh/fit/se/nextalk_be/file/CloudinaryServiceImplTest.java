package iuh.fit.se.nextalk_be.file;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import iuh.fit.se.nextalk_be.entity.MediaAsset;
import iuh.fit.se.nextalk_be.repository.MediaAssetRepository;
import iuh.fit.se.nextalk_be.service.impl.CloudinaryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceImplTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    private CloudinaryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CloudinaryServiceImpl(cloudinary, mediaAssetRepository);
    }

    @Test
    void uploadFile_ReusesExistingAssetWithSameContent() throws IOException {
        MockMultipartFile file = file("same image".getBytes());
        MediaAsset existing = MediaAsset.builder()
                .hash("stored-hash")
                .url("https://res.cloudinary.com/demo/image/upload/existing.png")
                .publicId("nextalk/assets/existing")
                .resourceType("image")
                .format("png")
                .size(file.getSize())
                .build();
        when(mediaAssetRepository.findById(any())).thenReturn(Optional.of(existing));

        Map result = service.uploadFile(file);

        assertEquals(existing.getUrl(), result.get("secure_url"));
        assertEquals(existing.getPublicId(), result.get("public_id"));
        assertEquals(true, result.get("deduplicated"));
        verify(cloudinary, never()).uploader();
    }

    @Test
    void uploadFile_UploadsAndStoresNewAsset() throws IOException {
        MockMultipartFile file = file("new image".getBytes());
        when(mediaAssetRepository.findById(any())).thenReturn(Optional.empty());
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/new.png",
                "public_id", "nextalk/assets/hash",
                "resource_type", "image",
                "format", "png"
        ));

        Map result = service.uploadFile(file);

        assertEquals("https://res.cloudinary.com/demo/image/upload/new.png", result.get("secure_url"));
        verify(uploader).upload(any(byte[].class), any(Map.class));
        verify(mediaAssetRepository).save(any(MediaAsset.class));
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "photo.png", MediaType.IMAGE_PNG_VALUE, bytes);
    }
}
