package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.SummaryMessagePayload;
import iuh.fit.se.nextalk_be.entity.MediaAsset;
import iuh.fit.se.nextalk_be.entity.MediaAssetStatus;
import iuh.fit.se.nextalk_be.entity.MessageAttachment;
import iuh.fit.se.nextalk_be.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds Gemini request parts from trusted NexTalk image attachments.
 *
 * <p>Only assets registered by NexTalk and hosted on an explicitly allowed HTTPS
 * host are downloaded. This prevents chat content from turning the backend into
 * an arbitrary URL fetcher.</p>
 */
@Service
@RequiredArgsConstructor
public class GeminiMultimodalService {

    private static final Logger log = LoggerFactory.getLogger(GeminiMultimodalService.class);
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/heic",
            "image/heif"
    );

    private final MediaAssetRepository mediaAssetRepository;
    private final CloudinaryService cloudinaryService;
    private final RestTemplate restTemplate;

    @Value("${app.ai-multimodal.max-images:3}")
    private int maxImages;

    @Value("${app.ai-multimodal.max-image-bytes:5242880}")
    private long maxImageBytes;

    @Value("${app.ai-multimodal.max-total-image-bytes:10485760}")
    private long maxTotalImageBytes;

    @Value("${app.ai-multimodal.allowed-hosts:res.cloudinary.com,api.cloudinary.com}")
    private String allowedHosts;

    public List<Map<String, Object>> buildParts(String prompt, List<SummaryMessagePayload> messages) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt));

        List<ImageCandidate> candidates = recentImageCandidates(messages);
        long totalImageBytes = 0;
        for (ImageCandidate candidate : candidates) {
            Optional<LoadedImage> loadedImage = loadImage(candidate.attachment());
            if (loadedImage.isEmpty()) continue;
            LoadedImage image = loadedImage.get();
            if (totalImageBytes + image.bytes().length > maxTotalImageBytes) continue;
            totalImageBytes += image.bytes().length;
            parts.add(Map.of("text", imageLabel(candidate.message(), candidate.attachment())));
            parts.add(Map.of("inlineData", Map.of(
                    "mimeType", image.contentType(),
                    "data", Base64.getEncoder().encodeToString(image.bytes())
            )));
        }
        return parts;
    }

    public String describeAttachments(List<MessageAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }
        long images = attachments.stream().filter(this::isImageAttachment).count();
        long videos = attachments.stream().filter(attachment -> hasType(attachment, "VIDEO")).count();
        long audio = attachments.stream().filter(attachment -> hasType(attachment, "AUDIO")).count();
        long files = attachments.size() - images - videos - audio;

        List<String> descriptions = new ArrayList<>();
        if (images > 0) descriptions.add(images == 1 ? "1 ảnh" : images + " ảnh");
        if (videos > 0) descriptions.add(videos == 1 ? "1 video" : videos + " video");
        if (audio > 0) descriptions.add(audio == 1 ? "1 audio" : audio + " audio");
        if (files > 0) descriptions.add(files == 1 ? "1 tệp" : files + " tệp");
        return descriptions.isEmpty() ? "" : "[" + String.join(", ", descriptions) + "]";
    }

    private List<ImageCandidate> recentImageCandidates(List<SummaryMessagePayload> messages) {
        if (messages == null || messages.isEmpty() || maxImages <= 0) {
            return List.of();
        }
        List<ImageCandidate> all = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        for (SummaryMessagePayload message : messages) {
            if (message == null || message.getAttachments() == null) continue;
            for (MessageAttachment attachment : message.getAttachments()) {
                if (!isImageAttachment(attachment)
                        || attachment.getUrl() == null
                        || attachment.getUrl().isBlank()
                        || !seenUrls.add(attachment.getUrl())) {
                    continue;
                }
                all.add(new ImageCandidate(message, attachment));
            }
        }
        int safeLimit = Math.max(0, Math.min(maxImages, 5));
        return all.size() <= safeLimit
                ? all
                : new ArrayList<>(all.subList(all.size() - safeLimit, all.size()));
    }

    private Optional<LoadedImage> loadImage(MessageAttachment attachment) {
        Optional<MediaAsset> registeredAsset = mediaAssetRepository.findByUrl(attachment.getUrl());
        if (registeredAsset.isEmpty()) {
            log.debug("Skipping unregistered image attachment in Gemini context");
            return Optional.empty();
        }

        MediaAsset asset = registeredAsset.get();
        if (asset.getStatus() == null || !asset.getStatus().isShareable()
                || asset.getSize() == null || asset.getSize() <= 0 || asset.getSize() > maxImageBytes) {
            log.debug("Skipping Gemini image attachment because its registered size is outside the limit");
            return Optional.empty();
        }

        URI uri;
        try {
            uri = URI.create(cloudinaryService.createDownloadUrl(asset));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        if (!isAllowedUri(uri)) {
            log.warn("Skipping registered Gemini image on a non-allowlisted host");
            return Optional.empty();
        }

        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(uri, byte[].class);
            byte[] bytes = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful()
                    || bytes == null
                    || bytes.length == 0
                    || bytes.length > maxImageBytes) {
                return Optional.empty();
            }

            MediaType responseType = response.getHeaders().getContentType();
            String contentType = normalizeContentType(
                    responseType == null ? asset.getContentType() : responseType.toString()
            );
            if (!SUPPORTED_IMAGE_TYPES.contains(contentType)) {
                return Optional.empty();
            }
            return Optional.of(new LoadedImage(bytes, contentType));
        } catch (RestClientException exception) {
            log.debug("Could not load a Gemini image attachment: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private boolean isAllowedUri(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return configuredHosts().stream()
                .anyMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed));
    }

    private Set<String> configuredHosts() {
        Set<String> hosts = new HashSet<>();
        if (allowedHosts != null) {
            for (String value : allowedHosts.split(",")) {
                String host = value.trim().toLowerCase(Locale.ROOT);
                if (!host.isBlank()) hosts.add(host);
            }
        }
        return hosts;
    }

    private boolean isImageAttachment(MessageAttachment attachment) {
        return hasType(attachment, "IMAGE");
    }

    private boolean hasType(MessageAttachment attachment, String type) {
        return attachment != null && type.equalsIgnoreCase(attachment.getType());
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) return "";
        int separator = contentType.indexOf(';');
        return (separator >= 0 ? contentType.substring(0, separator) : contentType)
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String imageLabel(SummaryMessagePayload message, MessageAttachment attachment) {
        String sender = message.getSenderUsername() == null || message.getSenderUsername().isBlank()
                ? "Người dùng"
                : message.getSenderUsername();
        String caption = message.getContent() == null ? "" : message.getContent().trim();
        String name = attachment.getName() == null || attachment.getName().isBlank()
                ? ""
                : " (" + attachment.getName().trim() + ")";
        return caption.isBlank()
                ? "Ảnh" + name + " do " + sender + " gửi:"
                : "Ảnh" + name + " do " + sender + " gửi, kèm chú thích: " + caption;
    }

    private record ImageCandidate(SummaryMessagePayload message, MessageAttachment attachment) {
    }

    private record LoadedImage(byte[] bytes, String contentType) {
    }
}
