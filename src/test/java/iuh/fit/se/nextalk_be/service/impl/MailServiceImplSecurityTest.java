package iuh.fit.se.nextalk_be.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MailServiceImplSecurityTest {

    @Test
    void mailFailureDoesNotLogRecipientOrCredentialLink() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenThrow(new IllegalStateException("provider unavailable"));
        MailServiceImpl service = new MailServiceImpl(sender);
        String recipient = "private-user@example.test";
        String link = "https://app.example.test/reset?token=secret-test-token";

        Logger logger = (Logger) LoggerFactory.getLogger(MailServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.sendPasswordResetEmail(recipient, link);
            service.sendVerificationEmail(recipient, link);
        } finally {
            logger.detachAppender(appender);
        }

        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(java.util.stream.Collectors.joining("\n"));
        assertFalse(logs.contains(recipient));
        assertFalse(logs.contains("secret-test-token"));
        assertFalse(logs.contains(link));
    }
}
