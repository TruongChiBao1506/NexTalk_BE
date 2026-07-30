package iuh.fit.se.nextalk_be.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import iuh.fit.se.nextalk_be.dto.request.ImageEditOperation;
import iuh.fit.se.nextalk_be.dto.request.ImageEditRequest;
import iuh.fit.se.nextalk_be.dto.response.FileUploadResponse;
import iuh.fit.se.nextalk_be.dto.response.ImageEditResponse;
import iuh.fit.se.nextalk_be.entity.MediaAsset;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.service.CloudinaryService;
import iuh.fit.se.nextalk_be.service.MediaAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryImageEditServiceImplTest {
    private static final String SOURCE_URL =
            "https://res.cloudinary.com/demo/image/upload/v123/nextalk/assets/source.png";

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MediaAuthorizationService mediaAuthorizationService;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private RestTemplate restTemplate;

    private CloudinaryImageEditServiceImpl service;

    @BeforeEach
    void setUp() {
        Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", "demo",
                "api_key", "key",
                "api_secret", "secret",
                "secure", true
        ));
        service = new CloudinaryImageEditServiceImpl(
                messageRepository,
                mediaAuthorizationService,
                cloudinaryService,
                restTemplate,
                cloudinary
        );
        ReflectionTestUtils.setField(service, "enabled", true);
    }

    @Test
    void buildTransformation_MapsEverySupportedOperation() {
        ImageEditRequest request = request(ImageEditOperation.REMOVE);
        request.setSubject("người phía sau (bên trái)");
        assertEquals(
                "e_gen_remove:prompt_người%20phía%20sau%20bên%20trái;multiple_true;remove-shadow_true",
                service.buildTransformation(request)
        );

        request.setOperation(ImageEditOperation.REPLACE);
        request.setReplacement("chậu cây");
        assertEquals(
                "e_gen_replace:from_người%20phía%20sau%20bên%20trái;to_chậu%20cây;preserve-geometry_true",
                service.buildTransformation(request)
        );

        request.setOperation(ImageEditOperation.RECOLOR);
        request.setColor("#4F46E5");
        assertEquals(
                "e_gen_recolor:prompt_(người%20phía%20sau%20bên%20trái);to-color_4f46e5",
                service.buildTransformation(request)
        );

        request.setOperation(ImageEditOperation.BACKGROUND_REPLACE);
        request.setPrompt("bãi biển");
        assertEquals(
                "e_gen_background_replace:prompt_bãi%20biển",
                service.buildTransformation(request)
        );

        request.setOperation(ImageEditOperation.FILL);
        request.setAspectRatio("16:9");
        assertEquals(
                "ar_16:9,b_gen_fill:prompt_bãi%20biển,c_pad",
                service.buildTransformation(request)
        );

        request.setOperation(ImageEditOperation.RESTORE);
        assertEquals("e_gen_restore", service.buildTransformation(request));
    }

    @Test
    void edit_GeneratesSignedCloudinaryUrlAndStoresResult() throws Exception {
        ImageEditRequest request = request(ImageEditOperation.REMOVE);
        request.setSubject("red car");
        Message message = Message.builder()
                .content(SOURCE_URL)
                .messageType(MessageType.IMAGE)
                .build();
        byte[] generated = new byte[]{1, 2, 3, 4};
        MediaAsset authorizedAsset = MediaAsset.builder()
                .hash("source-hash")
                .publicId("nextalk/assets/source")
                .resourceType("image")
                .format("png")
                .build();

        when(messageRepository.findById("message-1")).thenReturn(Optional.of(message));
        when(mediaAuthorizationService.assertCanDownload(SOURCE_URL)).thenReturn(authorizedAsset);
        when(restTemplate.getForEntity(any(URI.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(generated));
        when(cloudinaryService.uploadGeneratedImage(eq(generated), eq("image/png"), any()))
                .thenReturn(FileUploadResponse.builder()
                        .url("https://res.cloudinary.com/demo/image/upload/edited.png")
                        .publicId("nextalk/assets/edited")
                        .fileName("edited.png")
                        .contentType("image/png")
                        .size((long) generated.length)
                        .build());

        ImageEditResponse result = service.edit(request);

        assertEquals("cloudinary/remove", result.getModel());
        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForEntity(uriCaptor.capture(), eq(byte[].class));
        String generatedUrl = uriCaptor.getValue().toString();
        assertTrue(generatedUrl.contains("/s--"));
        assertTrue(generatedUrl.contains("e_gen_remove:prompt_red%20car"));
        assertTrue(generatedUrl.contains("/nextalk/assets/source.png"));
        verify(mediaAuthorizationService).assertCanDownload(SOURCE_URL);
        verify(mediaAuthorizationService).claimUpload(result.getUrl());
    }

    @Test
    void buildTransformation_RejectsInvalidColorAndAspectRatio() {
        ImageEditRequest recolor = request(ImageEditOperation.RECOLOR);
        recolor.setSubject("shirt");
        recolor.setColor("red");
        assertThrows(BadRequestException.class, () -> service.buildTransformation(recolor));

        ImageEditRequest fill = request(ImageEditOperation.FILL);
        fill.setAspectRatio("21:9");
        assertThrows(BadRequestException.class, () -> service.buildTransformation(fill));
    }

    private ImageEditRequest request(ImageEditOperation operation) {
        ImageEditRequest request = new ImageEditRequest();
        request.setMessageId("message-1");
        request.setSourceUrl(SOURCE_URL);
        request.setOperation(operation);
        return request;
    }
}
