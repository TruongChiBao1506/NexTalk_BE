package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.response.LinkPreviewResponse;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkPreviewServiceImplTest {

    private final LinkPreviewServiceImpl service = new LinkPreviewServiceImpl();

    @Test
    void parseTikTokOEmbed_UsesOfficialThumbnailAndMetadata() throws Exception {
        String json = """
                {
                  "title": "Video test",
                  "author_name": "Creator",
                  "thumbnail_url": "https://p16-sign.tiktokcdn.example/video-cover.jpeg",
                  "provider_name": "TikTok"
                }
                """;

        Optional<LinkPreviewResponse> result = service.parseTikTokOEmbed(
                json,
                "https://www.tiktok.com/@creator/video/123"
        );

        assertTrue(result.isPresent());
        assertEquals("Video test", result.get().getTitle());
        assertEquals("Tác giả: Creator", result.get().getDescription());
        assertEquals("https://p16-sign.tiktokcdn.example/video-cover.jpeg", result.get().getImage());
        assertEquals("TikTok", result.get().getSiteName());
    }

    @Test
    void parseTikTokOEmbed_RejectsUnsafeThumbnailScheme() throws Exception {
        String json = """
                {
                  "title": "Video test",
                  "thumbnail_url": "javascript:alert(1)",
                  "provider_name": "TikTok"
                }
                """;

        assertTrue(service.parseTikTokOEmbed(json, "https://www.tiktok.com/@creator/video/123").isEmpty());
    }

    @Test
    void isTikTokUrl_OnlyAcceptsTikTokHosts() {
        assertTrue(service.isTikTokUrl("https://vt.tiktok.com/example"));
        assertTrue(service.isTikTokUrl("https://www.tiktok.com/@creator/video/123"));
        assertFalse(service.isTikTokUrl("https://tiktok.com.evil.example/video/123"));
        assertFalse(service.isTikTokUrl("https://example.com/?next=tiktok.com"));
    }
}
