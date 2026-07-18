package iuh.fit.se.nextalk_be.file;

import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.MediaAsset;
import iuh.fit.se.nextalk_be.entity.MessageAttachment;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.MediaAssetRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.service.MediaAuthorizationService;
import iuh.fit.se.nextalk_be.service.UserService;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MediaAuthorizationServiceTest {
    @Test
    void onlyOwnerCanAttachUploadToConversation() {
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        ConversationRepository conversations = mock(ConversationRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        UserService users = mock(UserService.class);
        MediaAuthorizationService service = new MediaAuthorizationService(assets, conversations, messages, users);

        User owner = User.builder().username("owner").build();
        owner.setId("owner-id");
        User outsider = User.builder().username("outsider").build();
        outsider.setId("outsider-id");
        Conversation conversation = Conversation.builder().members(Set.of(owner, outsider)).build();
        conversation.setId("conversation-id");
        MediaAsset asset = MediaAsset.builder()
                .url("https://res.cloudinary.com/test/file.png")
                .allowedUserIds(new HashSet<>(Set.of(owner.getId())))
                .conversationIds(new HashSet<>())
                .build();
        when(assets.findByUrl(asset.getUrl())).thenReturn(Optional.of(asset));
        MessageAttachment attachment = MessageAttachment.builder().url(asset.getUrl()).type("IMAGE").build();

        assertThrows(BadRequestException.class,
                () -> service.authorizeForConversation(List.of(attachment), outsider, conversation));

        service.authorizeForConversation(List.of(attachment), owner, conversation);
        verify(assets).save(asset);
    }
}
