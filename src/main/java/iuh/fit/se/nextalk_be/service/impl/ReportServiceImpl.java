package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.request.CreateReportRequest;
import iuh.fit.se.nextalk_be.dto.response.UserReportResponse;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.entity.UserReport;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.UserReportRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.service.ModerationAiService;
import iuh.fit.se.nextalk_be.service.ReportService;
import iuh.fit.se.nextalk_be.service.UserService;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final UserReportRepository userReportRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final ModerationAiService moderationAiService;
    private final RateLimitService rateLimitService;

    @Value("${app.rate-limit.ai-moderation.limit:5}")
    private int moderationRateLimit;

    @Value("${app.rate-limit.ai-moderation.window-seconds:3600}")
    private long moderationRateWindowSeconds;

    @Override
    public UserReportResponse createReport(CreateReportRequest request) {
        User currentUser = userService.getCurrentAuthenticatedUser();
        rateLimitService.check("ai:moderation", currentUser.getId(), moderationRateLimit,
                Duration.ofSeconds(moderationRateWindowSeconds));
        User reportedUser = userRepository.findById(request.getReportedUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Reported user not found"));

        UserReport report = UserReport.builder()
                .reporter(currentUser)
                .reportedUser(reportedUser)
                .conversationId(request.getConversationId())
                .reason(request.getReason())
                .description(request.getDescription())
                .status("PENDING")
                .build();

        UserReport savedReport = userReportRepository.save(report);

        // Call AI moderation asynchronously
        moderationAiService.evaluateReportAsync(savedReport.getId());

        return mapToResponse(savedReport);
    }

    private UserReportResponse mapToResponse(UserReport report) {
        return UserReportResponse.builder()
                .id(report.getId())
                .reporterId(report.getReporter().getId())
                .reportedUserId(report.getReportedUser().getId())
                .conversationId(report.getConversationId())
                .reason(report.getReason())
                .description(report.getDescription())
                .status(report.getStatus())
                .aiVerdict(report.getAiVerdict())
                .aiReasoning(report.getAiReasoning())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
