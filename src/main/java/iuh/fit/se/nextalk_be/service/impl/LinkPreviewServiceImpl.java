package iuh.fit.se.nextalk_be.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.nextalk_be.dto.response.LinkPreviewResponse;
import iuh.fit.se.nextalk_be.service.LinkPreviewService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LinkPreviewServiceImpl implements LinkPreviewService {

    private static final Pattern URL_PATTERN = Pattern.compile("(?:https?://|www\\.)[^\\s<>\"]+|[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/[^\\s<>\"]*)?", Pattern.CASE_INSENSITIVE);
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_BODY_SIZE = 1024 * 512;
    private static final int MAX_REDIRECTS = 5;
    private static final String TIKTOK_OEMBED_ENDPOINT = "https://www.tiktok.com/oembed?url=";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<LinkPreviewResponse> createPreview(String content) {
        String url = extractFirstUrl(content);
        if (url == null) {
            return Optional.empty();
        }

        try {
            String safeUrl = resolveSafeUrl(url);
            if (isTikTokUrl(safeUrl)) {
                Optional<LinkPreviewResponse> tiktokPreview = fetchTikTokOEmbed(safeUrl);
                if (tiktokPreview.isPresent()) {
                    return tiktokPreview;
                }
            }
            Document document = fetchDocument(safeUrl);
            String resolvedUrl = isBlank(document.location()) ? safeUrl : resolveSafeUrl(document.location());

            if (isTikTokUrl(resolvedUrl) && !resolvedUrl.equals(safeUrl)) {
                Optional<LinkPreviewResponse> tiktokPreview = fetchTikTokOEmbed(resolvedUrl);
                if (tiktokPreview.isPresent()) {
                    return tiktokPreview;
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
            String image = absoluteUrl(document, firstNonBlank(
                    meta(document, "property", "og:image"),
                    meta(document, "name", "twitter:image")
            ));
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

            return Optional.of(LinkPreviewResponse.builder()
                    .url(resolvedUrl)
                    .title(truncate(title, 180))
                    .description(truncate(description, 260))
                    .image(image)
                    .siteName(truncate(siteName, 80))
                    .build());
        } catch (Exception e) {
            // A URL can be private message content. Never include it or exception
            // messages (which may also contain it) in application logs.
            log.debug("Unable to create link preview ({})", e.getClass().getSimpleName());
            return Optional.empty();
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

    Optional<LinkPreviewResponse> parseTikTokOEmbed(String json, String videoUrl) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        String image = safeHttpUrl(textValue(root, "thumbnail_url"));
        if (isBlank(image)) {
            return Optional.empty();
        }
        String author = textValue(root, "author_name");
        return Optional.of(LinkPreviewResponse.builder()
                .url(videoUrl)
                .title(truncate(firstNonBlank(textValue(root, "title"), "Video trên TikTok"), 180))
                .description(truncate(isBlank(author) ? null : "Tác giả: " + author, 260))
                .image(image)
                .siteName(truncate(firstNonBlank(textValue(root, "provider_name"), "TikTok"), 80))
                .build());
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
}
