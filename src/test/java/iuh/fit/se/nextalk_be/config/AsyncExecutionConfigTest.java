package iuh.fit.se.nextalk_be.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsyncExecutionConfigTest {

    @Test
    void createsBoundedExecutor() {
        ThreadPoolTaskExecutor executor =
                new AsyncExecutionConfig().applicationTaskExecutor(2, 4, 25, 30);
        executor.initialize();
        try {
            assertEquals(2, executor.getCorePoolSize());
            assertEquals(4, executor.getMaxPoolSize());
            assertEquals(25, executor.getQueueCapacity());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void rejectsInvalidPoolConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AsyncExecutionConfig().applicationTaskExecutor(4, 2, 25, 30)
        );
    }
}
