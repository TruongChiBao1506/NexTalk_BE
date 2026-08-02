package iuh.fit.se.nextalk_be.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncExecutionConfig {

    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    @Primary
    public ThreadPoolTaskExecutor applicationTaskExecutor(
            @Value("${app.async.core-pool-size:4}") int corePoolSize,
            @Value("${app.async.max-pool-size:8}") int maxPoolSize,
            @Value("${app.async.queue-capacity:200}") int queueCapacity,
            @Value("${app.async.keep-alive-seconds:60}") int keepAliveSeconds
    ) {
        if (corePoolSize < 1 || maxPoolSize < corePoolSize || queueCapacity < 0 || keepAliveSeconds < 0) {
            throw new IllegalArgumentException("Invalid bounded async executor configuration");
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("nextalk-async-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    @Bean(name = "linkPreviewTaskExecutor")
    public ThreadPoolTaskExecutor linkPreviewTaskExecutor(
            @Value("${app.link-preview.async.core-pool-size:2}") int corePoolSize,
            @Value("${app.link-preview.async.max-pool-size:4}") int maxPoolSize,
            @Value("${app.link-preview.async.queue-capacity:100}") int queueCapacity
    ) {
        if (corePoolSize < 1 || maxPoolSize < corePoolSize || queueCapacity < 0) {
            throw new IllegalArgumentException("Invalid link preview executor configuration");
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("nextalk-link-preview-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        // A preview is optional. Under saturation, dropping it is safer than
        // making the message sending thread perform a slow network request.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
