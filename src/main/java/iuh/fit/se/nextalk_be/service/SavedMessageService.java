package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.response.MessageResponse;
import iuh.fit.se.nextalk_be.dto.response.SavedMessageResponse;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.SavedMessage;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.SavedMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SavedMessageService {
    private final SavedMessageRepository savedMessageRepository;
    private final MessageRepository messageRepository;
    private final MessageService messageService;
    private final UserService userService;

    @Transactional
    public SavedMessageResponse save(String messageId) {
        User user = userService.getCurrentAuthenticatedUser();
        MessageResponse visibleMessage = messageService.getMessageForCurrentUser(messageId);
        SavedMessage existing = savedMessageRepository.findByUserIdAndMessageId(user.getId(), messageId).orElse(null);
        if (existing != null) return map(existing, visibleMessage);

        Message message = messageRepository.findById(messageId).orElseThrow();
        try {
            SavedMessage saved = savedMessageRepository.save(SavedMessage.builder().user(user).message(message).build());
            return map(saved, visibleMessage);
        } catch (DuplicateKeyException ignored) {
            SavedMessage saved = savedMessageRepository.findByUserIdAndMessageId(user.getId(), messageId)
                    .orElseThrow();
            return map(saved, visibleMessage);
        }
    }

    public List<SavedMessageResponse> getMine(int limit) {
        User user = userService.getCurrentAuthenticatedUser();
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<SavedMessageResponse> result = new ArrayList<>();
        for (SavedMessage saved : savedMessageRepository.findAllByUserIdOrderByCreatedAtDesc(
                user.getId(), PageRequest.of(0, safeLimit))) {
            if (saved.getMessage() == null) continue;
            try {
                MessageResponse message = messageService.getMessageForCurrentUser(saved.getMessage().getId());
                if (!message.isRecalled()) result.add(map(saved, message));
            } catch (BadRequestException | ResourceNotFoundException ignored) {
                // A saved pointer must never restore access after leaving a conversation,
                // deleting the message for oneself, or losing access to the source.
            }
        }
        return result;
    }

    @Transactional
    public void remove(String messageId) {
        User user = userService.getCurrentAuthenticatedUser();
        savedMessageRepository.deleteByUserIdAndMessageId(user.getId(), messageId);
    }

    private SavedMessageResponse map(SavedMessage saved, MessageResponse message) {
        return SavedMessageResponse.builder()
                .id(saved.getId())
                .savedAt(saved.getCreatedAt())
                .conversationName(saved.getMessage() != null
                        && saved.getMessage().getConversation() != null
                        ? saved.getMessage().getConversation().getName()
                        : null)
                .message(message)
                .build();
    }
}
