package iuh.fit.se.nextalk_be.file;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.utils.ObjectUtils;
import iuh.fit.se.nextalk_be.entity.MediaAsset;
import iuh.fit.se.nextalk_be.entity.MediaAssetStatus;
import iuh.fit.se.nextalk_be.repository.MediaAssetRepository;
import iuh.fit.se.nextalk_be.security.FileContentInspector;
import iuh.fit.se.nextalk_be.security.MalwareScanner;
import iuh.fit.se.nextalk_be.service.impl.CloudinaryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceImplTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private FileContentInspector fileContentInspector;

    @Mock
    private MalwareScanner malwareScanner;

    private CloudinaryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CloudinaryServiceImpl(
                cloudinary, mediaAssetRepository, fileContentInspector, malwareScanner);
        lenient().when(cloudinary.url().resourceType(any()).type(any()).secure(true).signed(true)
                .format(any()).generate(any())).thenReturn(
                "https://res.cloudinary.com/demo/image/authenticated/signed.png");
        lenient().when(mediaAssetRepository.save(any(MediaAsset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(malwareScanner.assertClean(any(byte[].class))).thenReturn(true);
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://api.example.test");
    }

    @Test
    void uploadFile_ReusesExistingAssetWithSameContent() throws IOException {
        MockMultipartFile file = file("same image".getBytes());
        MediaAsset existing = MediaAsset.builder()
                .hash("stored-hash")
                .url("https://res.cloudinary.com/demo/image/upload/existing.png")
                .storageUrl("https://res.cloudinary.com/demo/image/authenticated/existing.png")
                .publicId("nextalk/assets/existing")
                .resourceType("image")
                .format("png")
                .size(file.getSize())
                .status(MediaAssetStatus.CLEAN)
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

        assertEquals(true, ((String) result.get("secure_url"))
                .startsWith("https://api.example.test/api/files/content/"));
        verify(uploader).upload(any(byte[].class), any(Map.class));
        verify(mediaAssetRepository, org.mockito.Mockito.times(2)).save(any(MediaAsset.class));
    }

    @Test
    void uploadFile_BasicModeMarksAssetAsValidatedButNotMalwareScanned() throws IOException {
        MockMultipartFile file = file("basic image".getBytes());
        when(malwareScanner.assertClean(any(byte[].class))).thenReturn(false);
        when(mediaAssetRepository.findById(any())).thenReturn(Optional.empty());
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/basic.png",
                "public_id", "nextalk/assets/basic",
                "resource_type", "image",
                "format", "png"
        ));

        service.uploadFile(file);

        ArgumentCaptor<MediaAsset> savedAssets = ArgumentCaptor.forClass(MediaAsset.class);
        verify(mediaAssetRepository, org.mockito.Mockito.times(2)).save(savedAssets.capture());
        MediaAsset published = savedAssets.getAllValues().get(1);
        assertEquals(MediaAssetStatus.BASIC_VALIDATED, published.getStatus());
        assertEquals(null, published.getScannedAt());
        verify(fileContentInspector).validateBasicMode(MediaType.IMAGE_PNG_VALUE);
    }

    @Test
    void uploadFile_UploadsZipAsRawAsset() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "NopBai.zip",
                "application/zip",
                "zip content".getBytes()
        );
        when(mediaAssetRepository.findById(any())).thenReturn(Optional.empty());
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/raw/upload/NopBai.zip",
                "public_id", "nextalk/assets/hash",
                "resource_type", "raw",
                "format", "zip"
        ));

        service.uploadFile(file);

        ArgumentCaptor<Map> options = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(any(byte[].class), options.capture());
        assertEquals("raw", options.getValue().get("resource_type"));
        verify(mediaAssetRepository, org.mockito.Mockito.times(2)).save(any(MediaAsset.class));
    }

    @Test
    void uploadFile_RejectsZipAboveRawAssetLimit() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large-folder.zip",
                "application/zip",
                new byte[10 * 1024 * 1024 + 1]
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.uploadFile(file)
        );

        assertEquals("Raw file exceeds the Cloudinary limit of 10 MB", error.getMessage());
        verify(cloudinary, never()).uploader();
    }

    @Test
    void createDownloadUrl_UsesFreePlanPrivateDownloadWithTenMinuteExpiry() throws Exception {
        MediaAsset asset = MediaAsset.builder()
                .publicId("nextalk/assets/private-file")
                .resourceType("raw")
                .format("pdf")
                .build();
        String privateUrl =
                "https://api.cloudinary.com/v1_1/demo/raw/download?signature=signed";
        when(cloudinary.privateDownload(eq(asset.getPublicId()), eq(asset.getFormat()), any(Map.class)))
                .thenReturn(privateUrl);
        long before = Instant.now().plusSeconds(590).getEpochSecond();

        String result = service.createDownloadUrl(asset);

        long after = Instant.now().plusSeconds(610).getEpochSecond();
        ArgumentCaptor<Map> options = ArgumentCaptor.forClass(Map.class);
        verify(cloudinary).privateDownload(eq(asset.getPublicId()), eq(asset.getFormat()), options.capture());
        assertEquals(privateUrl, result);
        assertEquals("raw", options.getValue().get("resource_type"));
        assertEquals("authenticated", options.getValue().get("type"));
        assertEquals(false, options.getValue().get("attachment"));
        long expiresAt = ((Number) options.getValue().get("expires_at")).longValue();
        assertTrue(expiresAt >= before && expiresAt <= after);
    }

    @Test
    void createDownloadUrl_CloudinarySdkGeneratesSignedApiUrlWithoutPremiumToken() {
        Cloudinary freePlanCloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", "demo",
                "api_key", "test-api-key",
                "api_secret", "test-api-secret",
                "secure", true
        ));
        CloudinaryServiceImpl freePlanService = new CloudinaryServiceImpl(
                freePlanCloudinary, mediaAssetRepository, fileContentInspector, malwareScanner);
        MediaAsset asset = MediaAsset.builder()
                .publicId("nextalk/assets/private-image")
                .resourceType("image")
                .format("png")
                .build();

        URI uri = URI.create(freePlanService.createDownloadUrl(asset));

        assertEquals("https", uri.getScheme());
        assertEquals("api.cloudinary.com", uri.getHost());
        assertTrue(uri.getPath().endsWith("/demo/image/download"));
        assertTrue(uri.getQuery().contains("expires_at="));
        assertTrue(uri.getQuery().contains("signature="));
        assertTrue(uri.getQuery().contains("api_key=test-api-key"));
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "photo.png", MediaType.IMAGE_PNG_VALUE, bytes);
    }
}
