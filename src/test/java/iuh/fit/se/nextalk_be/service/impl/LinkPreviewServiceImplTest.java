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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    void parseYouTubeOEmbed_UsesThumbnailForShortsUrl() throws Exception {
        String json = """
                {
                  "title": "Shorts test",
                  "author_name": "Creator",
                  "thumbnail_url": "https://i.ytimg.com/vi/video-id/hq2.jpg",
                  "provider_name": "YouTube"
                }
                """;

        Optional<LinkPreviewResponse> result = service.parseYouTubeOEmbed(
                json,
                "https://www.youtube.com/shorts/video-id"
        );

        assertTrue(result.isPresent());
        assertEquals(LinkPreviewType.VIDEO, result.get().getType());
        assertEquals("YOUTUBE", result.get().getProvider());
        assertEquals("Shorts test", result.get().getTitle());
        assertEquals("Tác giả: Creator", result.get().getDescription());
        assertEquals("https://i.ytimg.com/vi/video-id/hq2.jpg", result.get().getThumbnailUrl());
        assertEquals("youtube.com", result.get().getDisplayDomain());
    }

    @Test
    void parseFacebookOEmbed_UsesPublicVideoThumbnailWithoutEmbedHtml() throws Exception {
        String json = """
                {
                  "type": "video",
                  "title": "Public reel",
                  "author_name": "Creator",
                  "thumbnail_url": "https://scontent.example/reel-cover.jpg",
                  "provider_name": "Facebook",
                  "html": "<script>untrustedEmbed()</script>"
                }
                """;

        Optional<LinkPreviewResponse> result = service.parseFacebookOEmbed(
                json,
                "https://www.facebook.com/reel/123"
        );

        assertTrue(result.isPresent());
        assertEquals(LinkPreviewType.VIDEO, result.get().getType());
        assertEquals("FACEBOOK", result.get().getProvider());
        assertEquals("Public reel", result.get().getTitle());
        assertEquals("Tác giả: Creator", result.get().getDescription());
        assertEquals("https://scontent.example/reel-cover.jpg", result.get().getThumbnailUrl());
        assertEquals("facebook.com", result.get().getDisplayDomain());
        assertFalse(result.get().toString().contains("untrustedEmbed"));
    }

    @Test
    void parseFacebookOEmbed_WithoutSafeThumbnailFallsBackToOpenGraph() throws Exception {
        String json = """
                {
                  "type": "rich",
                  "author_name": "Creator",
                  "thumbnail_url": "javascript:alert(1)",
                  "provider_name": "Facebook",
                  "html": "<div>Public post</div>"
                }
                """;

        assertTrue(service.parseFacebookOEmbed(json, "https://www.facebook.com/posts/123").isEmpty());
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
    void isFacebookUrl_OnlyAcceptsFacebookHostsAndRecognizesVideoLinks() {
        assertTrue(service.isFacebookUrl("https://www.facebook.com/share/r/abc"));
        assertTrue(service.isFacebookUrl("https://fb.watch/abc"));
        assertTrue(service.isFacebookVideoUrl("https://www.facebook.com/reel/123"));
        assertTrue(service.isFacebookVideoUrl("https://www.facebook.com/watch/?v=123"));
        assertFalse(service.isFacebookVideoUrl("https://www.facebook.com/posts/123"));
        assertFalse(service.isFacebookUrl("https://facebook.com.evil.example/reel/123"));
        assertFalse(service.isFacebookUrl("https://example.com/?next=facebook.com"));
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
        assertEquals(LinkPreviewType.DEFAULT, service.classifyType(
                "https://www.facebook.com/posts/123", "website", "summary", null));
        assertEquals(LinkPreviewType.VIDEO, service.classifyType(
                "https://www.facebook.com/reel/123", "website", "summary", null));
    }

    @Test
    void classifyProvider_RejectsLookalikeDomains() {
        assertEquals("YOUTUBE", service.classifyProvider("https://www.youtube.com/watch?v=video-id"));
        assertEquals("TIKTOK", service.classifyProvider("https://vt.tiktok.com/video-id"));
        assertEquals("FACEBOOK", service.classifyProvider("https://www.facebook.com/reel/video-id"));
        assertEquals(null, service.classifyProvider("https://youtube.com.evil.example/watch?v=video-id"));
        assertEquals(null, service.classifyProvider("https://tiktok.example/video-id"));
        assertEquals(null, service.classifyProvider("https://facebook.com.evil.example/reel/video-id"));
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

    @Test
    void containsPreviewableUrl_DetectsLinksWithoutFetchingThem() {
        assertTrue(service.containsPreviewableUrl("Xem https://www.youtube.com/watch?v=video-id"));
        assertTrue(service.containsPreviewableUrl("Xem example.com/article"));
        assertFalse(service.containsPreviewableUrl("Tin nhan khong co lien ket"));
        assertFalse(service.containsPreviewableUrl(null));
    }

    @Test
    void coordinatePreviewLoad_CoalescesConcurrentRequestsForTheSameUrl() throws Exception {
        LinkPreviewServiceImpl coordinatingService = new LinkPreviewServiceImpl();
        String url = "https://video.example/watch/shared";
        LinkPreviewResponse preview = LinkPreviewResponse.builder()
                .version(2)
                .url(url)
                .type(LinkPreviewType.VIDEO)
                .build();
        AtomicInteger loadCount = new AtomicInteger();
        CountDownLatch firstLoadStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstLoad = new CountDownLatch(1);
        CountDownLatch secondCallStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Optional<LinkPreviewResponse>> first = executor.submit(() ->
                    coordinatingService.coordinatePreviewLoad(url, () -> {
                        loadCount.incrementAndGet();
                        firstLoadStarted.countDown();
                        try {
                            releaseFirstLoad.await(2, TimeUnit.SECONDS);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                        return Optional.of(preview);
                    }));

            assertTrue(firstLoadStarted.await(1, TimeUnit.SECONDS));
            Future<Optional<LinkPreviewResponse>> second = executor.submit(() -> {
                secondCallStarted.countDown();
                return coordinatingService.coordinatePreviewLoad(url, () -> {
                    loadCount.incrementAndGet();
                    return Optional.of(preview);
                });
            });

            assertTrue(secondCallStarted.await(1, TimeUnit.SECONDS));
            assertFalse(second.isDone());
            assertEquals(1, loadCount.get());

            releaseFirstLoad.countDown();
            assertEquals(preview, first.get(1, TimeUnit.SECONDS).orElseThrow());
            assertEquals(preview, second.get(1, TimeUnit.SECONDS).orElseThrow());
            assertEquals(1, loadCount.get());
        } finally {
            releaseFirstLoad.countDown();
            executor.shutdownNow();
        }
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
