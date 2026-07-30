package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.request.ImageEditRequest;
import iuh.fit.se.nextalk_be.dto.response.FileUploadResponse;
import iuh.fit.se.nextalk_be.dto.response.ImageEditResponse;
import iuh.fit.se.nextalk_be.entity.MediaAsset;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.exception.AppException;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.service.impl.CloudflareImageEditServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudflareImageEditServiceImplTest {
    private static final String SOURCE_URL =
            "https://res.cloudinary.com/demo/image/upload/source.png";
    private static final String PRIVATE_SOURCE_URL =
            "https://api.cloudinary.com/v1_1/demo/image/download?signature=signed";

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MediaAuthorizationService mediaAuthorizationService;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private RestTemplate restTemplate;

    private CloudflareImageEditServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CloudflareImageEditServiceImpl(
                messageRepository,
                mediaAuthorizationService,
                cloudinaryService,
                restTemplate
        );
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "accountId", "account123");
        ReflectionTestUtils.setField(service, "apiToken", "token123");
        ReflectionTestUtils.setField(service, "model", "@cf/black-forest-labs/flux-2-klein-4b");
        ReflectionTestUtils.setField(
                service,
                "cloudflareUrl",
                "https://api.cloudflare.com/client/v4/accounts/{accountId}/ai/run/{model}"
        );
    }

    @Test
    void edit_SendsResizedMultipartImageAndStoresCloudflareResult() throws Exception {
        ImageEditRequest request = request();
        Message message = Message.builder()
                .content(SOURCE_URL)
                .messageType(MessageType.IMAGE)
                .build();
        byte[] largeSource = png(900, 600);
        byte[] generated = png(512, 512);
        MediaAsset authorizedAsset = MediaAsset.builder()
                .hash("source-hash")
                .publicId("nextalk/assets/source")
                .resourceType("image")
                .format("png")
                .build();

        when(messageRepository.findById("message-1")).thenReturn(Optional.of(message));
        when(mediaAuthorizationService.assertCanDownload(SOURCE_URL)).thenReturn(authorizedAsset);
        when(cloudinaryService.createDownloadUrl(authorizedAsset)).thenReturn(PRIVATE_SOURCE_URL);
        when(restTemplate.getForEntity(URI.create(PRIVATE_SOURCE_URL), byte[].class))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .body(largeSource));
        when(restTemplate.postForEntity(any(URI.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "success", true,
                        "result", Map.of("image", Base64.getEncoder().encodeToString(generated))
                )));
        when(cloudinaryService.uploadGeneratedImage(any(byte[].class), eq("image/png"), any()))
                .thenReturn(FileUploadResponse.builder()
                        .url("https://res.cloudinary.com/demo/image/upload/edited.png")
                        .publicId("nextalk/edited")
                        .fileName("edited.png")
                        .contentType("image/png")
                        .size((long) generated.length)
                        .build());

        ImageEditResponse result = service.edit(request);

        assertEquals("https://res.cloudinary.com/demo/image/upload/edited.png", result.getUrl());
        assertEquals("@cf/black-forest-labs/flux-2-klein-4b", result.getModel());

        ArgumentCaptor<URI> endpointCaptor = ArgumentCaptor.forClass(URI.class);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(endpointCaptor.capture(), entityCaptor.capture(), eq(Map.class));
        assertEquals(
                "https://api.cloudflare.com/client/v4/accounts/account123/ai/run/@cf/black-forest-labs/flux-2-klein-4b",
                endpointCaptor.getValue().toString()
        );
        assertEquals("Bearer token123", entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));

        @SuppressWarnings("unchecked")
        MultiValueMap<String, Object> form =
                (MultiValueMap<String, Object>) entityCaptor.getValue().getBody();
        assertNotNull(form);
        Resource input = (Resource) form.getFirst("input_image_0");
        assertNotNull(input);
        BufferedImage resized = ImageIO.read(new ByteArrayInputStream(input.getContentAsByteArray()));
        assertTrue(resized.getWidth() < 512);
        assertTrue(resized.getHeight() < 512);
        assertEquals("1024", form.getFirst("width"));
        assertEquals("683", form.getFirst("height"));
    }

    @Test
    void edit_ReturnsServiceUnavailableWhenCloudflareCredentialsAreMissing() {
        ReflectionTestUtils.setField(service, "apiToken", "");

        AppException exception = assertThrows(AppException.class, () -> service.edit(request()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertTrue(exception.getMessage().contains("CLOUDFLARE_AI_TOKEN"));
        verify(messageRepository, never()).findById(any());
    }

    private ImageEditRequest request() {
        ImageEditRequest request = new ImageEditRequest();
        request.setMessageId("message-1");
        request.setSourceUrl(SOURCE_URL);
        request.setPrompt("Make the sky warmer");
        return request;
    }

    private byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
