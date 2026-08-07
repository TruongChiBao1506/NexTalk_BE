package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageNotificationDispatchStatus;
import iuh.fit.se.nextalk_be.entity.MessageType;
import iuh.fit.se.nextalk_be.entity.NotificationType;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

@Component
@Slf4j
public class MessageNotificationDispatcher {
    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ConversationNotificationPreferenceService preferenceService;
    private final boolean schedulerEnabled;
    private final int batchSize;
    private final Duration leaseDuration;

    public MessageNotificationDispatcher(
            MongoTemplate mongoTemplate,
            UserRepository userRepository,
            NotificationService notificationService,
            ConversationNotificationPreferenceService preferenceService,
            @Value("${app.message-notification-dispatch.worker-enabled:true}") boolean schedulerEnabled,
            @Value("${app.message-notification-dispatch.batch-size:50}") int batchSize,
            @Value("${app.message-notification-dispatch.lease-duration:30s}") Duration leaseDuration
    ) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.preferenceService = preferenceService;
        this.schedulerEnabled = schedulerEnabled;
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
        this.leaseDuration = leaseDuration;
    }

    public void dispatchNow(String messageId) {
        Message claimed = claim(Query.query(Criteria.where("_id").is(messageId)), LocalDateTime.now());
        if (claimed != null) {
            processClaimed(claimed);
        }
    }

    @Scheduled(fixedDelayString = "${app.message-notification-dispatch.poll-delay-ms:1000}")
    public void runScheduledBatch() {
        if (!schedulerEnabled) return;
        try {
            for (int index = 0; index < batchSize; index++) {
                Message claimed = claim(new Query(), LocalDateTime.now());
                if (claimed == null) return;
                processClaimed(claimed);
            }
        } catch (Exception e) {
            log.warn("[NotificationDispatcher] Transient DB error during batch poll: {}", e.getMessage());
        }
    }

    private Message claim(Query base, LocalDateTime now) {
        Criteria duePending = new Criteria().andOperator(
                Criteria.where("notificationDispatchStatus").is(MessageNotificationDispatchStatus.PENDING),
                new Criteria().orOperator(
                        Criteria.where("notificationDispatchNextAttemptAt").lte(now),
                        Criteria.where("notificationDispatchNextAttemptAt").exists(false),
                        Criteria.where("notificationDispatchNextAttemptAt").is(null)));
        Criteria expiredLease = new Criteria().andOperator(
                Criteria.where("notificationDispatchStatus").is(MessageNotificationDispatchStatus.PROCESSING),
                Criteria.where("notificationDispatchLeaseUntil").lte(now));
        base.addCriteria(new Criteria().orOperator(duePending, expiredLease));
        base.with(Sort.by(Sort.Direction.ASC, "notificationDispatchNextAttemptAt", "createdAt", "_id"));
        String leaseId = UUID.randomUUID().toString();
        Update update = new Update()
                .set("notificationDispatchStatus", MessageNotificationDispatchStatus.PROCESSING)
                .set("notificationDispatchLeaseUntil", now.plus(leaseDuration))
                .set("notificationDispatchLeaseId", leaseId)
                .inc("notificationDispatchAttempts", 1)
                .set("updatedAt", now);
        return mongoTemplate.findAndModify(
                base,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Message.class);
    }

    private void processClaimed(Message message) {
        try {
            dispatchRecipients(message);
            complete(message, LocalDateTime.now());
        } catch (RuntimeException failure) {
            retry(message, LocalDateTime.now());
            log.warn("Message notification dispatch scheduled for retry; errorCode=DISPATCH_ERROR");
        }
    }

    private void dispatchRecipients(Message message) {
        Conversation conversation = message.getConversation();
        if (conversation == null || conversation.getMembers() == null) {
            throw new IllegalStateException("Conversation unavailable for notification dispatch");
        }
        User sender = userRepository.findById(message.getSenderId()).orElse(message.getSender());
        if (sender == null) {
            throw new IllegalStateException("Sender unavailable for notification dispatch");
        }

        String contentPreview = contentPreview(message);
        String priorityPrefix = priorityPrefix(message);
        String notificationPrefix = notificationPrefix(message);
        boolean mentionsEveryone = metadataFlag(message, "mentionAll");
        boolean suppressAll = metadataFlag(message, "silent")
                || (metadataFlag(message, "strangerMessage")
                && "MEDIUM".equals(metadataValue(message, "spamRisk")));

        for (User memberRef : conversation.getMembers()) {
            if (memberRef == null || memberRef.getId().equals(sender.getId())) continue;
            User member = userRepository.findById(memberRef.getId()).orElse(memberRef);
            boolean mentioned = isMentioned(message, member.getId());
            if (suppressAll || !preferenceService.shouldNotify(conversation, member.getId(), mentioned)) {
                continue;
            }

            boolean hiddenConversation = conversation.getHiddenByUsers() != null
                    && conversation.getHiddenByUsers().contains(member.getId());
            String notificationContent = notificationContent(
                    conversation, sender, contentPreview, priorityPrefix,
                    notificationPrefix, mentioned, mentionsEveryone, hiddenConversation);
            String pushBody = pushBody(
                    contentPreview, priorityPrefix, mentioned, mentionsEveryone, hiddenConversation);
            notificationService.createChatNotification(
                    member,
                    mentioned ? NotificationType.MENTION : NotificationType.NEW_MESSAGE,
                    notificationContent,
                    message.getId(),
                    conversation.getId(),
                    conversation.getName(),
                    sender.getId(),
                    sender.getUsername(),
                    sender.getAvatarUrl(),
                    pushBody);
        }
    }

    private void complete(Message message, LocalDateTime now) {
        Update update = new Update()
                .set("notificationDispatchStatus", MessageNotificationDispatchStatus.COMPLETE)
                .unset("notificationDispatchLeaseUntil")
                .unset("notificationDispatchLeaseId")
                .set("updatedAt", now);
        mongoTemplate.updateFirst(ownedLease(message), update, Message.class);
    }

    private void retry(Message message, LocalDateTime now) {
        long delaySeconds = Math.min(60L, 1L << Math.min(6, Math.max(0, message.getNotificationDispatchAttempts() - 1)));
        Update update = new Update()
                .set("notificationDispatchStatus", MessageNotificationDispatchStatus.PENDING)
                .set("notificationDispatchNextAttemptAt", now.plusSeconds(delaySeconds))
                .unset("notificationDispatchLeaseUntil")
                .unset("notificationDispatchLeaseId")
                .set("updatedAt", now);
        mongoTemplate.updateFirst(ownedLease(message), update, Message.class);
    }

    private Query ownedLease(Message message) {
        return Query.query(Criteria.where("_id").is(message.getId())
                .and("notificationDispatchStatus").is(MessageNotificationDispatchStatus.PROCESSING)
                .and("notificationDispatchLeaseId").is(message.getNotificationDispatchLeaseId()));
    }

    private static String contentPreview(Message message) {
        if (message.getMessageType() == MessageType.IMAGE) return "[Hình ảnh]";
        if (message.getMessageType() == MessageType.VIDEO) return "[Video]";
        if (message.getMessageType() == MessageType.AUDIO) return "[Tin nhắn thoại]";
        if (message.getMessageType() == MessageType.FILE) return "[Tệp đính kèm]";
        if (message.getMessageType() == MessageType.GIF) return "Đã gửi một ảnh GIF";
        String content = message.getContent() == null ? "" : message.getContent();
        return content.replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">");
    }

    private static String priorityPrefix(Message message) {
        Object priority = metadataValue(message, "priority");
        if ("IMPORTANT".equals(priority)) return "[Quan trọng] ";
        if ("URGENT".equals(priority)) return "[Khẩn cấp] ";
        return "";
    }

    private static String notificationPrefix(Message message) {
        Object priority = metadataValue(message, "priority");
        if ("IMPORTANT".equals(priority)) return "⚠️ ";
        if ("URGENT".equals(priority)) return "🚨 ";
        return "";
    }

    private static String notificationContent(
            Conversation conversation,
            User sender,
            String preview,
            String priorityPrefix,
            String notificationPrefix,
            boolean mentioned,
            boolean mentionsEveryone,
            boolean hidden
    ) {
        if (hidden) {
            return notificationPrefix + (mentioned
                    ? "Bạn được nhắc trong một cuộc trò chuyện"
                    : "Bạn có tin nhắn mới");
        }
        String shortPreview = preview.length() > 60 ? preview.substring(0, 57) + "..." : preview;
        if (mentioned) {
            String mentionLabel = mentionsEveryone ? " đã nhắc đến mọi người" : " đã nhắc đến bạn";
            String conversationLabel = conversation.getName() == null || conversation.getName().isBlank()
                    ? "" : " trong " + conversation.getName().trim();
            return notificationPrefix + sender.getUsername() + mentionLabel + conversationLabel
                    + ": " + priorityPrefix + shortPreview;
        }
        return notificationPrefix + "Bạn có tin nhắn mới từ " + sender.getUsername()
                + ": " + priorityPrefix + shortPreview;
    }

    private static String pushBody(
            String preview,
            String priorityPrefix,
            boolean mentioned,
            boolean mentionsEveryone,
            boolean hidden
    ) {
        if (hidden) return mentioned ? "Bạn được nhắc trong một cuộc trò chuyện" : "Bạn có tin nhắn mới";
        String body = priorityPrefix + preview;
        if (mentioned) {
            return (mentionsEveryone ? "Đã nhắc đến mọi người: " : "Đã nhắc đến bạn: ") + body;
        }
        return body;
    }

    private static boolean isMentioned(Message message, String userId) {
        if (metadataFlag(message, "mentionAll")) return true;
        Object value = metadataValue(message, "mentionedUserIds");
        return value instanceof Collection<?> ids && ids.stream().anyMatch(userId::equals);
    }

    private static boolean metadataFlag(Message message, String key) {
        return Boolean.TRUE.equals(metadataValue(message, key));
    }

    private static Object metadataValue(Message message, String key) {
        return message.getMetadata() == null ? null : message.getMetadata().get(key);
    }
}
