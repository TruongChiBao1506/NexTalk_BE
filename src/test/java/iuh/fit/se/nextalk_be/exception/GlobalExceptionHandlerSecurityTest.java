package iuh.fit.se.nextalk_be.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerSecurityTest {

    @Test
    void genericResponseDoesNotExposeInternalExceptionMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        var response = handler.handleGenericException(
                new IllegalStateException("mongodb://private-host/internal-secret"));
        logger.detachAppender(appender);

        String clientMessage = response.getBody().getMessage();
        String serverLogs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(java.util.stream.Collectors.joining("\n"));
        assertFalse(clientMessage.contains("private-host"));
        assertFalse(clientMessage.contains("internal-secret"));
        assertFalse(serverLogs.contains("private-host"));
        assertFalse(serverLogs.contains("internal-secret"));
        assertNotNull(response.getHeaders().getFirst("X-Correlation-Id"));
    }
}
