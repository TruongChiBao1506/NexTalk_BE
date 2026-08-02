package iuh.fit.se.nextalk_be.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.nextalk_be.dto.response.LinkPreviewAction;
import iuh.fit.se.nextalk_be.dto.response.LinkPreviewResponse;
import iuh.fit.se.nextalk_be.dto.response.LinkPreviewType;
import iuh.fit.se.nextalk_be.service.LinkPreviewService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LinkPreviewServiceImpl implements LinkPreviewService {

    private static final Pattern URL_PATTERN = Pattern.compile("(?:https?://|www\\.)[^\\s<>\"]+|[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/[^\\s<>\"]*)?", Pattern.CASE_INSENSITIVE);
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_BODY_SIZE = 1024 * 512;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_CACHE_ENTRIES = 500;
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final int PREVIEW_SCHEMA_VERSION = 2;
    private static final String TIKTOK_OEMBED_ENDPOINT = "https://www.tiktok.com/oembed?url=";
    private static final String YOUTUBE_OEMBED_ENDPOINT = "https://www.youtube.com/oembed?format=json&url=";
    private static final String FACEBOOK_OEMBED_POST_ENDPOINT = "https://graph.facebook.com/oembed_post";
    private static final String FACEBOOK_OEMBED_VIDEO_ENDPOINT = "https://graph.facebook.com/oembed_video";
    private static final String PROVIDER_TIKTOK = "TIKTOK";
    private static final String PROVIDER_YOUTUBE = "YOUTUBE";
    private static final String PROVIDER_FACEBOOK = "FACEBOOK";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, CachedPreview> previewCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Optional<LinkPreviewResponse>>> inFlightPreviews = new ConcurrentHashMap<>();
    private final Clock clock;

    @Value("${app.link-preview.facebook-access-token:}")
    private String facebookAccessToken;

    public LinkPreviewServiceImpl() {
        this(Clock.systemUTC());
    }

    LinkPreviewServiceImpl(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean containsPreviewableUrl(String content) {
        return extractFirstUrl(content) != null;
    }

    @Override
    public Optional<LinkPreviewResponse> createPreview(String content) {
        String url = extractFirstUrl(content);
        if (url == null) {
            return Optional.empty();
        }

        try {
            String safeUrl = resolveSafeUrl(url);
            Optional<LinkPreviewResponse> cachedPreview = getCachedPreview(safeUrl);
            if (cachedPreview.isPresent()) {
                return cachedPreview;
            }
            return coordinatePreviewLoad(safeUrl, () -> createPreviewForSafeUrl(safeUrl));
        } catch (Exception e) {
            // A URL can be private message content. Never include it or exception
            // messages (which may also contain it) in application logs.
            log.debug("Unable to create link preview ({})", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<LinkPreviewResponse> createPreviewForSafeUrl(String safeUrl) {
        try {
            Optional<LinkPreviewResponse> cachedPreview = getCachedPreview(safeUrl);
            if (cachedPreview.isPresent()) {
                return cachedPreview;
            }
            Optional<LinkPreviewResponse> providerPreview = fetchProviderOEmbed(safeUrl);
            if (providerPreview.isPresent()) {
                return cachePreview(safeUrl, providerPreview.get());
            }
            Document document = fetchDocument(safeUrl);
            String resolvedUrl = isBlank(document.location()) ? safeUrl : resolveSafeUrl(document.location());

            if (!resolvedUrl.equals(safeUrl)) {
                providerPreview = fetchProviderOEmbed(resolvedUrl);
                if (providerPreview.isPresent()) {
                    return cachePreview(safeUrl, providerPreview.get());
                }
            }

            String title = firstNonBlank(
                    meta(document, "property", "og:title"),
                    meta(document, "name", "twitter:title"),
                    document.title()
            );
            String description = firstNonBlank(
                    meta(document, "property", "og:description"),
                    meta(document, "name", "description"),
                    meta(document, "name", "twitter:description")
            );
            String image = safeHttpUrl(absoluteUrl(document, firstNonBlank(
                    meta(document, "property", "og:image"),
                    meta(document, "name", "twitter:image")
            )));
            String siteName = firstNonBlank(
                    meta(document, "property", "og:site_name"),
                    URI.create(resolvedUrl).getHost()
            );

            if (isBlank(title)) {
                title = firstNonBlank(siteName, URI.create(resolvedUrl).getHost(), resolvedUrl);
            }

            if (isBlank(title) && isBlank(description) && isBlank(image)) {
                return Optional.empty();
            }

            LinkPreviewType previewType = classifyType(
                    resolvedUrl,
                    meta(document, "property", "og:type"),
                    firstNonBlank(
                            meta(document, "name", "twitter:card"),
                            meta(document, "property", "twitter:card")
                    ),
                    firstNonBlank(
                            meta(document, "property", "og:video"),
                            meta(document, "property", "og:video:url"),
                            meta(document, "name", "twitter:player")
                    )
            );

            return cachePreview(safeUrl, buildPreview(
                    resolvedUrl,
                    previewType,
                    classifyProvider(resolvedUrl),
                    title,
                    description,
                    image,
                    siteName
            ));
        } catch (Exception e) {
            // A URL can be private message content. Never include it or exception
            // messages (which may also contain it) in application logs.
            log.debug("Unable to create link preview ({})", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    Optional<LinkPreviewResponse> coordinatePreviewLoad(
            String safeUrl,
            Supplier<Optional<LinkPreviewResponse>> loader
    ) {
        Optional<LinkPreviewResponse> cachedPreview = getCachedPreview(safeUrl);
        if (cachedPreview.isPresent()) {
            return cachedPreview;
        }

        CompletableFuture<Optional<LinkPreviewResponse>> ownerFuture = new CompletableFuture<>();
        CompletableFuture<Optional<LinkPreviewResponse>> existingFuture =
                inFlightPreviews.putIfAbsent(safeUrl, ownerFuture);
        if (existingFuture != null) {
            return existingFuture.join();
        }

        try {
            Optional<LinkPreviewResponse> result = loader.get();
            result.ifPresent(preview -> cachePreview(safeUrl, preview));
            ownerFuture.complete(result);
            return result;
        } catch (RuntimeException | Error error) {
            ownerFuture.completeExceptionally(error);
            throw error;
        } finally {
            inFlightPreviews.remove(safeUrl, ownerFuture);
        }
    }

    private Optional<LinkPreviewResponse> fetchTikTokOEmbed(String videoUrl) {
        try {
            String endpoint = TIKTOK_OEMBED_ENDPOINT
                    + URLEncoder.encode(videoUrl, StandardCharsets.UTF_8);
            String json = fetchJson(endpoint);
            return parseTikTokOEmbed(json, videoUrl);
        } catch (Exception e) {
            log.debug("TikTok oEmbed preview unavailable ({})", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<LinkPreviewResponse> fetchYouTubeOEmbed(String videoUrl) {
        try {
            String endpoint = YOUTUBE_OEMBED_ENDPOINT
                    + URLEncoder.encode(videoUrl, StandardCharsets.UTF_8);
            String json = fetchJson(endpoint);
            return parseYouTubeOEmbed(json, videoUrl);
        } catch (Exception e) {
            log.debug("YouTube oEmbed preview unavailable ({})", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<LinkPreviewResponse> fetchFacebookOEmbed(String facebookUrl) {
        if (isBlank(facebookAccessToken)) {
            return Optional.empty();
        }

        String[] endpoints = isFacebookVideoUrl(facebookUrl)
                ? new String[]{FACEBOOK_OEMBED_VIDEO_ENDPOINT, FACEBOOK_OEMBED_POST_ENDPOINT}
                : new String[]{FACEBOOK_OEMBED_POST_ENDPOINT};
        for (String endpoint : endpoints) {
            try {
                String requestUrl = endpoint
                        + "?omitscript=true&url=" + URLEncoder.encode(facebookUrl, StandardCharsets.UTF_8)
                        + "&access_token=" + URLEncoder.encode(facebookAccessToken.trim(), StandardCharsets.UTF_8);
                Optional<LinkPreviewResponse> preview = parseFacebookOEmbed(fetchJson(requestUrl), facebookUrl);
                if (preview.isPresent()) {
                    return preview;
                }
            } catch (Exception e) {
                // The access token and private message URL are deliberately omitted.
                log.debug("Facebook oEmbed preview unavailable ({})", e.getClass().getSimpleName());
            }
        }
        return Optional.empty();
    }

    private Optional<LinkPreviewResponse> fetchProviderOEmbed(String url) {
        if (isTikTokUrl(url)) {
            return fetchTikTokOEmbed(url);
        }
        if (isYouTubeUrl(url)) {
            return fetchYouTubeOEmbed(url);
        }
        if (isFacebookUrl(url)) {
            return fetchFacebookOEmbed(url);
        }
        return Optional.empty();
    }

    Optional<LinkPreviewResponse> parseTikTokOEmbed(String json, String videoUrl) throws Exception {
        return parseVideoOEmbed(json, videoUrl, PROVIDER_TIKTOK, "Video trên TikTok", "TikTok");
    }

    Optional<LinkPreviewResponse> parseYouTubeOEmbed(String json, String videoUrl) throws Exception {
        return parseVideoOEmbed(json, videoUrl, PROVIDER_YOUTUBE, "Video trên YouTube", "YouTube");
    }

    Optional<LinkPreviewResponse> parseFacebookOEmbed(String json, String facebookUrl) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        String image = safeHttpUrl(textValue(root, "thumbnail_url"));
        if (isBlank(image)) {
            // Meta does not guarantee a thumbnail for every public post. Returning
            // empty lets the normal Open Graph fetch run instead of replacing it
            // with a poorer text-only provider response.
            return Optional.empty();
        }

        String author = textValue(root, "author_name");
        String oEmbedType = firstNonBlank(textValue(root, "type"), "");
        LinkPreviewType type = "video".equalsIgnoreCase(oEmbedType) || isFacebookVideoUrl(facebookUrl)
                ? LinkPreviewType.VIDEO
                : "photo".equalsIgnoreCase(oEmbedType) ? LinkPreviewType.IMAGE : LinkPreviewType.DEFAULT;
        return Optional.of(buildPreview(
                facebookUrl,
                type,
                PROVIDER_FACEBOOK,
                firstNonBlank(textValue(root, "title"), author, "Nội dung trên Facebook"),
                isBlank(author) ? null : "Tác giả: " + author,
                image,
                firstNonBlank(textValue(root, "provider_name"), "Facebook")
        ));
    }

    private Optional<LinkPreviewResponse> parseVideoOEmbed(
            String json,
            String videoUrl,
            String provider,
            String fallbackTitle,
            String fallbackSiteName
    ) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        String image = safeHttpUrl(textValue(root, "thumbnail_url"));
        if (isBlank(image)) {
            return Optional.empty();
        }
        String author = textValue(root, "author_name");
        return Optional.of(buildPreview(
                videoUrl,
                LinkPreviewType.VIDEO,
                provider,
                firstNonBlank(textValue(root, "title"), fallbackTitle),
                isBlank(author) ? null : "Tác giả: " + author,
                image,
                firstNonBlank(textValue(root, "provider_name"), fallbackSiteName)
        ));
    }

    boolean isTikTokUrl(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && (host.equalsIgnoreCase("tiktok.com")
                    || host.toLowerCase(Locale.ROOT).endsWith(".tiktok.com"));
        } catch (Exception ignored) {
            return false;
        }
    }

    boolean isYouTubeUrl(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return normalizedHost.equals("youtu.be")
                    || normalizedHost.equals("youtube.com")
                    || normalizedHost.endsWith(".youtube.com")
                    || normalizedHost.equals("youtube-nocookie.com")
                    || normalizedHost.endsWith(".youtube-nocookie.com");
        } catch (Exception ignored) {
            return false;
        }
    }

    boolean isFacebookUrl(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return normalizedHost.equals("facebook.com")
                    || normalizedHost.endsWith(".facebook.com")
                    || normalizedHost.equals("fb.watch")
                    || normalizedHost.endsWith(".fb.watch")
                    || normalizedHost.equals("fb.me")
                    || normalizedHost.endsWith(".fb.me");
        } catch (Exception ignored) {
            return false;
        }
    }

    boolean isFacebookVideoUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!isFacebookUrl(url)) {
                return false;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("fb.watch") || host.endsWith(".fb.watch")) {
                return true;
            }
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            String query = uri.getRawQuery() == null ? "" : uri.getRawQuery().toLowerCase(Locale.ROOT);
            return path.contains("/reel/")
                    || path.contains("/reels/")
                    || path.contains("/videos/")
                    || path.startsWith("/watch/")
                    || path.startsWith("/share/r/")
                    || path.startsWith("/share/v/")
                    || query.matches("(?:^|.*&)v=[^&]+(?:&.*|$)");
        } catch (Exception ignored) {
            return false;
        }
    }

    String classifyProvider(String url) {
        if (isTikTokUrl(url)) {
            return PROVIDER_TIKTOK;
        }
        if (isYouTubeUrl(url)) {
            return PROVIDER_YOUTUBE;
        }
        if (isFacebookUrl(url)) {
            return PROVIDER_FACEBOOK;
        }
        return null;
    }

    LinkPreviewType classifyType(String url, String openGraphType, String twitterCard, String videoUrl) {
        if (isTikTokUrl(url) || isYouTubeUrl(url) || isFacebookVideoUrl(url)) {
            return LinkPreviewType.VIDEO;
        }

        String normalizedType = firstNonBlank(openGraphType, "");
        normalizedType = normalizedType == null ? "" : normalizedType.toLowerCase(Locale.ROOT);
        String normalizedCard = firstNonBlank(twitterCard, "");
        normalizedCard = normalizedCard == null ? "" : normalizedCard.toLowerCase(Locale.ROOT);

        if (!isBlank(videoUrl) || normalizedType.startsWith("video") || normalizedCard.equals("player")) {
            return LinkPreviewType.VIDEO;
        }
        if (normalizedType.startsWith("article") || normalizedType.equals("news")) {
            return LinkPreviewType.ARTICLE;
        }
        if (normalizedType.startsWith("music") || normalizedType.startsWith("audio")) {
            return LinkPreviewType.AUDIO;
        }
        if (normalizedType.startsWith("image")) {
            return LinkPreviewType.IMAGE;
        }
        return LinkPreviewType.DEFAULT;
    }

    private LinkPreviewResponse buildPreview(
            String url,
            LinkPreviewType type,
            String provider,
            String title,
            String description,
            String thumbnailUrl,
            String siteName
    ) {
        return LinkPreviewResponse.builder()
                .version(PREVIEW_SCHEMA_VERSION)
                .url(url)
                .canonicalUrl(url)
                .type(type == null ? LinkPreviewType.DEFAULT : type)
                .provider(provider)
                .title(truncate(title, 180))
                .description(truncate(description, 260))
                .image(thumbnailUrl)
                .thumbnailUrl(thumbnailUrl)
                .siteName(truncate(siteName, 80))
                .displayDomain(displayDomain(url))
                .action(LinkPreviewAction.OPEN_EXTERNAL)
                .build();
    }

    Optional<LinkPreviewResponse> getCachedPreview(String safeUrl) {
        CachedPreview cached = previewCache.get(safeUrl);
        if (cached == null) {
            return Optional.empty();
        }
        if (cached.expiresAtMillis() <= clock.millis()) {
            previewCache.remove(safeUrl, cached);
            return Optional.empty();
        }
        return Optional.of(cached.preview());
    }

    Optional<LinkPreviewResponse> cachePreview(String safeUrl, LinkPreviewResponse preview) {
        long now = clock.millis();
        if (previewCache.size() >= MAX_CACHE_ENTRIES) {
            previewCache.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
            if (previewCache.size() >= MAX_CACHE_ENTRIES) {
                previewCache.keySet().stream().findFirst().ifPresent(previewCache::remove);
            }
        }
        previewCache.put(safeUrl, new CachedPreview(preview, now + CACHE_TTL.toMillis()));
        return Optional.of(preview);
    }

    private String displayDomain(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return null;
            }
            return truncate(host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", ""), 120);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String fetchJson(String url) throws Exception {
        String currentUrl = resolveSafeUrl(url);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            Connection.Response response = Jsoup.connect(currentUrl)
                    .userAgent("NexTalk-LinkPreview/1.0")
                    .header("Accept", "application/json")
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_SIZE)
                    .followRedirects(false)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .execute();
            int status = response.statusCode();
            if (status >= 300 && status < 400 && response.header("Location") != null) {
                currentUrl = resolveSafeUrl(URI.create(currentUrl).resolve(response.header("Location")).toString());
                continue;
            }
            if (status < 200 || status >= 300) {
                throw new IllegalArgumentException("oEmbed endpoint returned a non-success status");
            }
            return response.body();
        }
        throw new IllegalArgumentException("Too many redirects");
    }

    private String textValue(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        return value != null && value.isTextual() ? value.asText().trim() : null;
    }

    private String safeHttpUrl(String rawUrl) {
        if (isBlank(rawUrl)) {
            return null;
        }
        try {
            URI uri = URI.create(rawUrl).normalize();
            if ("https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null) {
                return uri.toString();
            }
        } catch (Exception ignored) {
            // Invalid thumbnail URL is treated as unavailable metadata.
        }
        return null;
    }

    private String extractFirstUrl(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Matcher matcher = URL_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        String extracted = matcher.group().replaceAll("[),.]+$", "");
        if (!extracted.startsWith("http://") && !extracted.startsWith("https://")) {
            extracted = "https://" + extracted;
        }
        return extracted;
    }

    private Document fetchDocument(String url) throws Exception {
        String currentUrl = url;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            Connection.Response response = Jsoup.connect(currentUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9,vi;q=0.8")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_SIZE)
                    .followRedirects(false)
                    .ignoreContentType(true)
                    .execute();

            int status = response.statusCode();
            if (status >= 300 && status < 400 && response.header("Location") != null) {
                currentUrl = resolveSafeUrl(URI.create(currentUrl).resolve(response.header("Location")).toString());
                continue;
            }

            String contentType = response.contentType();
            if (contentType != null && !contentType.toLowerCase().contains("text/html")) {
                throw new IllegalArgumentException("URL did not return HTML");
            }
            return response.parse();
        }
        throw new IllegalArgumentException("Too many redirects");
    }

    private String resolveSafeUrl(String rawUrl) throws Exception {
        URI uri = URI.create(rawUrl).normalize();
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Unsupported URL scheme");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Invalid URL");
        }

        InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
        for (InetAddress address : addresses) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new IllegalArgumentException("Private network URL is not allowed");
            }
        }

        URL url = uri.toURL();
        return url.toExternalForm();
    }

    private String meta(Document document, String attribute, String value) {
        return document.selectFirst("meta[" + attribute + "=\"" + value + "\"]") != null
                ? document.selectFirst("meta[" + attribute + "=\"" + value + "\"]").attr("content")
                : null;
    }

    private String absoluteUrl(Document document, String url) {
        if (isBlank(url)) {
            return null;
        }
        return URI.create(document.baseUri()).resolve(url).toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1).trim() + "...";
    }

    private record CachedPreview(LinkPreviewResponse preview, long expiresAtMillis) {
    }
}
