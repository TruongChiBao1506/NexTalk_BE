package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.SummaryMessagePayload;
import iuh.fit.se.nextalk_be.entity.MediaAsset;
import iuh.fit.se.nextalk_be.entity.MessageAttachment;
import iuh.fit.se.nextalk_be.repository.MediaAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GeminiMultimodalServiceTest {

    private MediaAssetRepository mediaAssetRepository;
    private RestTemplate restTemplate;
    private GeminiMultimodalService service;

    @BeforeEach
    void setUp() {
        mediaAssetRepository = mock(MediaAssetRepository.class);
        restTemplate = mock(RestTemplate.class);
        service = new GeminiMultimodalService(mediaAssetRepository, restTemplate);
        ReflectionTestUtils.setField(service, "maxImages", 3);
        ReflectionTestUtils.setField(service, "maxImageBytes", 5_242_880L);
        ReflectionTestUtils.setField(service, "maxTotalImageBytes", 10_485_760L);
        ReflectionTestUtils.setField(service, "allowedHosts", "res.cloudinary.com");
    }

    @Test
    void buildsInlineDataForRegisteredCloudinaryImage() {
        String url = "https://res.cloudinary.com/demo/image/upload/photo.png";
        byte[] bytes = "image-bytes".getBytes(StandardCharsets.UTF_8);
        MessageAttachment attachment = MessageAttachment.builder()
                .url(url)
                .type("IMAGE")
                .name("photo.png")
                .size((long) bytes.length)
                .build();
        MediaAsset asset = MediaAsset.builder()
                .url(url)
                .contentType("image/png")
                .size((long) bytes.length)
                .build();
        when(mediaAssetRepository.findByUrl(url)).thenReturn(Optional.of(asset));
        when(restTemplate.getForEntity(URI.create(url), byte[].class))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .body(bytes));

        List<Map<String, Object>> parts = service.buildParts(
                "Hãy trả lời dựa trên ảnh.",
                List.of(SummaryMessagePayload.builder()
                        .senderUsername("Lan")
                        .content("Bạn xem giúp mình")
                        .attachments(List.of(attachment))
                        .build())
        );

        assertEquals(3, parts.size());
        assertEquals("Hãy trả lời dựa trên ảnh.", parts.get(0).get("text"));
        assertTrue(String.valueOf(parts.get(1).get("text")).contains("Lan"));
        Map<?, ?> inlineData = (Map<?, ?>) parts.get(2).get("inlineData");
        assertEquals("image/png", inlineData.get("mimeType"));
        assertEquals(Base64.getEncoder().encodeToString(bytes), inlineData.get("data"));
    }

    @Test
    void skipsUrlThatIsNotRegisteredByNexTalk() {
        String url = "https://example.com/private-network-proxy.png";
        MessageAttachment attachment = MessageAttachment.builder()
                .url(url)
                .type("IMAGE")
                .build();
        when(mediaAssetRepository.findByUrl(url)).thenReturn(Optional.empty());

        List<Map<String, Object>> parts = service.buildParts(
                "Prompt",
                List.of(SummaryMessagePayload.builder()
                        .attachments(List.of(attachment))
                        .build())
        );

        assertEquals(1, parts.size());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void skipsRegisteredAssetOutsideAllowedHosts() {
        String url = "https://images.example.com/photo.png";
        MessageAttachment attachment = MessageAttachment.builder()
                .url(url)
                .type("IMAGE")
                .build();
        when(mediaAssetRepository.findByUrl(url)).thenReturn(Optional.of(MediaAsset.builder()
                .url(url)
                .contentType("image/png")
                .size(100L)
                .build()));

        List<Map<String, Object>> parts = service.buildParts(
                "Prompt",
                List.of(SummaryMessagePayload.builder()
                        .attachments(List.of(attachment))
                        .build())
        );

        assertEquals(1, parts.size());
        verifyNoInteractions(restTemplate);
    }
}
