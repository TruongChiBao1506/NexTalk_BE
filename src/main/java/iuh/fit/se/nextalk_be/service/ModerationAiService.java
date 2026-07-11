package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.entity.UserReport;
import java.util.concurrent.CompletableFuture;

public interface ModerationAiService {
    void evaluateReportAsync(String reportId);
}
