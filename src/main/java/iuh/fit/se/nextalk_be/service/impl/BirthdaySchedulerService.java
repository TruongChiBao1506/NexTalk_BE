package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ConversationType;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.dto.response.MessageResponse;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.MessageStatusRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduled service that sends birthday congratulation messages
 * from NexTalk Moderator bot to users on their birthday.
 * Runs every day at 09:00 AM.
 */
@Service
@RequiredArgsConstructor
public class BirthdaySchedulerService {

    private static final Logger log = LoggerFactory.getLogger(BirthdaySchedulerService.class);
    private static final String BOT_EMAIL = "moderator@nextalk.local";
    private static final String BOT_AVATAR = "https://res.cloudinary.com/dp5r0dqqh/image/upload/v1783700471/nextalk/nnjdwhw3tfhjjjymbfgj.png";

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageStatusRepository messageStatusRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Runs every day at 09:00 AM to send birthday wishes.
     * Cron: second minute hour day month weekday
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendBirthdayWishes() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd"));
        log.info("BirthdayScheduler: checking birthdays for {}", today);

        List<User> candidates = userRepository.findByBirthdayNotNullAndEnableBirthdayNotificationTrue();

        for (User user : candidates) {
            try {
                String birthday = user.getBirthday();
                // Support both "YYYY-MM-dd" and "MM-dd" formats
                String monthDay = birthday.length() > 5 ? birthday.substring(5) : birthday;
                if (today.equals(monthDay)) {
                    sendBirthdayMessage(user);
                    log.info("BirthdayScheduler: sent birthday wish to {}", user.getUsername());
                }
            } catch (Exception e) {
                log.error("BirthdayScheduler: failed to send wish to user {}: {}", user.getId(), e.getMessage());
            }
        }
    }

    private User getOrCreateBot() {
        User bot = userRepository.findByEmail(BOT_EMAIL).orElseGet(() -> {
            User newBot = new User();
            newBot.setUsername("NexTalk Moderator");
            newBot.setEmail(BOT_EMAIL);
            newBot.setPassword("moderator_hidden_password");
            newBot.setAvatarUrl(BOT_AVATAR);
            newBot.setStatus("ONLINE");
            newBot.setVerified(true);
            return userRepository.save(newBot);
        });
        if (!BOT_AVATAR.equals(bot.getAvatarUrl())) {
            bot.setAvatarUrl(BOT_AVATAR);
            bot = userRepository.save(bot);
        }
        return bot;
    }

    private Conversation getOrCreateBotConversation(User user) {
        User bot = getOrCreateBot();
        org.bson.types.ObjectId botId = new org.bson.types.ObjectId(bot.getId());
        org.bson.types.ObjectId userId = new org.bson.types.ObjectId(user.getId());

        return conversationRepository.findPrivateConversationBetweenUsers(botId, userId).orElseGet(() -> {
            Conversation conv = new Conversation();
            conv.setType(ConversationType.PRIVATE);
            conv.setMembers(java.util.Set.of(bot, user));
            conv.setCreatedAt(LocalDateTime.now());
            conv.setUpdatedAt(LocalDateTime.now());
            return conversationRepository.save(conv);
        });
    }

    private void sendBirthdayMessage(User user) {
        User bot = getOrCreateBot();
        Conversation conversation = getOrCreateBotConversation(user);

        String content = "🎂 Chúc mừng sinh nhật " + user.getUsername() + "! "
                + "NexTalk chúc bạn một ngày sinh nhật thật vui vẻ, hạnh phúc và tràn đầy yêu thương! 🎉🎈";

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("systemType", "BIRTHDAY_WISH");
        metadata.put("botName", "NexTalk Moderator");

        Message message = Message.builder()
                .conversation(conversation)
                .sender(bot)
                .content(content)
                .messageType(MessageType.SYSTEM)
                .metadata(metadata)
                .build();

        Message saved = messageRepository.save(message);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        MessageResponse response = MessageResponse.builder()
                .id(saved.getId())
                .conversationId(conversation.getId())
                .senderId(bot.getId())
                .senderUsername(bot.getUsername())
                .content(content)
                .messageType(MessageType.SYSTEM.name())
                .attachments(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .statuses(messageStatusRepository.findAllByMessageId(saved.getId()).stream().map(status ->
                        iuh.fit.se.nextalk_be.dto.response.MessageStatusResponse.builder()
                                .userId(status.getUser().getId())
                                .username(status.getUser().getUsername())
                                .status(status.getStatus())
                                .updatedAt(status.getUpdatedAt() != null ? status.getUpdatedAt() : status.getCreatedAt())
                                .build()
                ).toList())
                .metadata(metadata)
                .build();

        messagingTemplate.convertAndSend("/topic/conversation/" + conversation.getId(), response);
        messagingTemplate.convertAndSendToUser(user.getId(), "/queue/messages", response);
    }
}
