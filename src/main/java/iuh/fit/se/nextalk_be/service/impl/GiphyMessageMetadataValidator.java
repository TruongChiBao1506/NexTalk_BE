package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class GiphyMessageMetadataValidator {
    private static final Pattern GIPHY_ID = Pattern.compile("^[A-Za-z0-9]{1,64}$");
    private static final Set<String> TEXT_FIELDS = Set.of("title", "altText", "username", "displayName", "sourceTld");
    private static final Set<String> URL_FIELDS = Set.of("giphyUrl", "profileUrl");

    public Map<String, Object> sanitize(String content, Map<String, Object> requestMetadata) {
        if (content == null || !GIPHY_ID.matcher(content).matches()) {
            throw new BadRequestException("Invalid GIPHY GIF identifier");
        }
        if (requestMetadata == null || !(requestMetadata.get("gif") instanceof Map<?, ?> rawGif)) {
            throw new BadRequestException("GIPHY attribution metadata is required");
        }
        if (!"GIPHY".equals(rawGif.get("provider")) || !content.equals(rawGif.get("id"))) {
            throw new BadRequestException("GIPHY metadata does not match the GIF identifier");
        }
        Map<String, Object> gif = new LinkedHashMap<>();
        gif.put("provider", "GIPHY");
        gif.put("id", content);
        gif.put("version", 1);
        for (String field : TEXT_FIELDS) putBoundedText(gif, field, rawGif.get(field), field.equals("altText") ? 500 : 200);
        for (String field : URL_FIELDS) putGiphyUrl(gif, field, rawGif.get(field));
        return new LinkedHashMap<>(Map.of("gif", gif));
    }

    private void putBoundedText(Map<String, Object> target, String key, Object value, int maxLength) {
        if (!(value instanceof String text) || text.isBlank()) return;
        String normalized = text.trim();
        target.put(key, normalized.substring(0, Math.min(normalized.length(), maxLength)));
    }

    private void putGiphyUrl(Map<String, Object> target, String key, Object value) {
        if (!(value instanceof String text) || text.isBlank()) return;
        try {
            URI uri = URI.create(text.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !(host.equalsIgnoreCase("giphy.com") || host.toLowerCase().endsWith(".giphy.com"))
                    || uri.getUserInfo() != null) {
                throw new BadRequestException("Invalid GIPHY attribution URL");
            }
            target.put(key, uri.toString());
        } catch (BadRequestException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Invalid GIPHY attribution URL");
        }
    }
}
