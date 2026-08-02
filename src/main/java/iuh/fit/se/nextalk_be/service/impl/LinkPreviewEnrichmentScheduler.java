package iuh.fit.se.nextalk_be.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LinkPreviewEnrichmentScheduler {

    private final ThreadPoolTaskExecutor executor;

    public LinkPreviewEnrichmentScheduler(
            @Qualifier("linkPreviewTaskExecutor") ThreadPoolTaskExecutor executor
    ) {
        this.executor = executor;
    }

    public boolean submit(Runnable task) {
        try {
            executor.execute(task);
            return true;
        } catch (TaskRejectedException exception) {
            log.debug("Link preview enrichment skipped because the executor is saturated");
            return false;
        }
    }
}
