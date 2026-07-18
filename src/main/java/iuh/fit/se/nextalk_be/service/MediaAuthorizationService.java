package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.MediaAsset;
import iuh.fit.se.nextalk_be.entity.MessageAttachment;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.MediaAssetRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaAuthorizationService {
    private final MediaAssetRepository mediaAssets;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final UserService userService;

    public void claimUpload(String url) {
        if (url == null) return;
        User user = userService.getCurrentAuthenticatedUser();
        MediaAsset asset = mediaAssets.findByUrl(url)
                .orElseThrow(() -> new ResourceNotFoundException("Uploaded asset was not registered"));
        ensureSets(asset);
        asset.getAllowedUserIds().add(user.getId());
        mediaAssets.save(asset);
    }

    public void authorizeForConversation(List<MessageAttachment> attachments, User user, Conversation conversation) {
        if (attachments == null || attachments.isEmpty()) return;
        for (MessageAttachment attachment : attachments) {
            if (attachment == null || attachment.getUrl() == null) continue;
            MediaAsset asset = mediaAssets.findByUrl(attachment.getUrl())
                    .orElseThrow(() -> new BadRequestException("Attachment is not a registered NexTalk upload"));
            ensureSets(asset);
            if (!canAccess(asset, user)) throw new BadRequestException("You do not have access to this attachment");
            asset.getAllowedUserIds().add(user.getId());
            asset.getConversationIds().add(conversation.getId());
            mediaAssets.save(asset);
        }
    }

    public void assertCanDownload(String url) {
        User user = userService.getCurrentAuthenticatedUser();
        MediaAsset asset = mediaAssets.findByUrl(url)
                .orElseThrow(() -> new ResourceNotFoundException("File is not registered"));
        ensureSets(asset);
        if (!canAccess(asset, user) && !canAccessLegacyMessage(url, user)) {
            throw new BadRequestException("You do not have access to this file");
        }
    }

    private boolean canAccess(MediaAsset asset, User user) {
        if (asset.getAllowedUserIds().contains(user.getId())) return true;
        return asset.getConversationIds().stream().anyMatch(id -> conversations.findById(id)
                .map(conversation -> isMember(conversation, user.getId())).orElse(false));
    }

    private boolean canAccessLegacyMessage(String url, User user) {
        return messages.findByAttachmentUrl(url).stream().anyMatch(message -> {
            String conversationId = message.getConversationId();
            return conversationId != null && conversations.findById(conversationId)
                    .map(conversation -> isMember(conversation, user.getId())).orElse(false);
        });
    }

    private boolean isMember(Conversation conversation, String userId) {
        return conversation.getMembers() != null && conversation.getMembers().stream()
                .anyMatch(member -> userId.equals(member.getId()));
    }

    private void ensureSets(MediaAsset asset) {
        if (asset.getAllowedUserIds() == null) asset.setAllowedUserIds(new HashSet<>());
        if (asset.getConversationIds() == null) asset.setConversationIds(new HashSet<>());
    }
}
