package iuh.fit.se.nextalk_be.config;

import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageStatus;
import iuh.fit.se.nextalk_be.repository.MessageRepository;
import iuh.fit.se.nextalk_be.repository.MessageStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageDenormalizationMigration implements ApplicationRunner {
    private static final int BATCH_SIZE = 250;
    private final MessageRepository messageRepository;
    private final MessageStatusRepository messageStatusRepository;

    @Override
    public void run(ApplicationArguments args) {
        long startedAt = System.nanoTime();
        int migratedMessages = migrateMessages();
        int migratedStatuses = migrateStatuses();
        if (migratedMessages + migratedStatuses > 0) {
            log.info("Message denormalization migration completed messages={} statuses={} elapsedMs={}",
                    migratedMessages, migratedStatuses, (System.nanoTime() - startedAt) / 1_000_000);
        }
    }

    private int migrateMessages() {
        int total = 0;
        while (true) {
            List<Message> batch = messageRepository.findByConversationIdIsNull(PageRequest.of(0, BATCH_SIZE));
            if (batch.isEmpty()) return total;
            messageRepository.saveAll(batch); // BeforeConvert callback fills denormalized fields.
            total += batch.size();
        }
    }

    private int migrateStatuses() {
        int total = 0;
        while (true) {
            List<MessageStatus> batch = messageStatusRepository.findByConversationIdIsNull(PageRequest.of(0, BATCH_SIZE));
            if (batch.isEmpty()) return total;
            messageStatusRepository.saveAll(batch);
            total += batch.size();
        }
    }
}
