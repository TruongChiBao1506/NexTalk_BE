package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.response.LinkPreviewResponse;

import java.util.Optional;

public interface LinkPreviewService {
    boolean containsPreviewableUrl(String content);

    Optional<LinkPreviewResponse> createPreview(String content);
}
