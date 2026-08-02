package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.response.LinkPreviewResponse;
import iuh.fit.se.nextalk_be.dto.response.LinkPreviewAction;
import iuh.fit.se.nextalk_be.dto.response.LinkPreviewType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

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
        assertEquals("https://p16-sign.tiktokcdn.example/video-cover.jpeg", result.get().getThumbnailUrl());
        assertEquals("TikTok", result.get().getSiteName());
        assertEquals(2, result.get().getVersion());
        assertEquals(LinkPreviewType.VIDEO, result.get().getType());
        assertEquals("TIKTOK", result.get().getProvider());
        assertEquals("tiktok.com", result.get().getDisplayDomain());
        assertEquals(LinkPreviewAction.OPEN_EXTERNAL, result.get().getAction());
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

    @Test
    void isYouTubeUrl_OnlyAcceptsYouTubeHosts() {
        assertTrue(service.isYouTubeUrl("https://youtu.be/video-id"));
        assertTrue(service.isYouTubeUrl("https://m.youtube.com/watch?v=video-id"));
        assertTrue(service.isYouTubeUrl("https://www.youtube-nocookie.com/embed/video-id"));
        assertFalse(service.isYouTubeUrl("https://youtube.com.evil.example/watch?v=video-id"));
        assertFalse(service.isYouTubeUrl("https://example.com/?next=youtube.com"));
    }

    @Test
    void classifyType_UsesMetadataInsteadOfAProviderList() {
        assertEquals(LinkPreviewType.VIDEO, service.classifyType(
                "https://video.example/watch/123", "video.other", null, null));
        assertEquals(LinkPreviewType.VIDEO, service.classifyType(
                "https://player.example/watch/123", null, "player", null));
        assertEquals(LinkPreviewType.ARTICLE, service.classifyType(
                "https://news.example/story", "article", "summary_large_image", null));
        assertEquals(LinkPreviewType.AUDIO, service.classifyType(
                "https://audio.example/track", "music.song", null, null));
        assertEquals(LinkPreviewType.DEFAULT, service.classifyType(
                "https://example.com/page", "website", "summary", null));
    }

    @Test
    void classifyProvider_RejectsLookalikeDomains() {
        assertEquals("YOUTUBE", service.classifyProvider("https://www.youtube.com/watch?v=video-id"));
        assertEquals("TIKTOK", service.classifyProvider("https://vt.tiktok.com/video-id"));
        assertEquals(null, service.classifyProvider("https://youtube.com.evil.example/watch?v=video-id"));
        assertEquals(null, service.classifyProvider("https://tiktok.example/video-id"));
    }

    @Test
    void previewCache_ReusesMetadataAndExpiresIt() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-02T08:00:00Z"));
        LinkPreviewServiceImpl cachedService = new LinkPreviewServiceImpl(clock);
        LinkPreviewResponse preview = LinkPreviewResponse.builder()
                .version(2)
                .url("https://video.example/watch/123")
                .type(LinkPreviewType.VIDEO)
                .build();

        cachedService.cachePreview(preview.getUrl(), preview);

        assertTrue(cachedService.getCachedPreview(preview.getUrl()).isPresent());
        assertEquals(preview, cachedService.getCachedPreview(preview.getUrl()).orElseThrow());
        assertTrue(cachedService.getCachedPreview("https://video.example/watch/missing").isEmpty());

        clock.advance(Duration.ofMinutes(31));

        assertTrue(cachedService.getCachedPreview(preview.getUrl()).isEmpty());
    }

    private static final class MutableClock extends Clock {
        private Instant currentInstant;

        private MutableClock(Instant currentInstant) {
            this.currentInstant = currentInstant;
        }

        private void advance(Duration duration) {
            currentInstant = currentInstant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }
}
