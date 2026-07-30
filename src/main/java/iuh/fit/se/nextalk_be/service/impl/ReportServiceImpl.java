package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.request.CreateReportRequest;
import iuh.fit.se.nextalk_be.dto.request.ModerationDecisionRequest;
import iuh.fit.se.nextalk_be.dto.response.UserReportResponse;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ModerationDecision;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.entity.UserReport;
import iuh.fit.se.nextalk_be.exception.ResourceNotFoundException;
import iuh.fit.se.nextalk_be.repository.UserReportRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.service.AccountSecurityService;
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
    private final ConversationRepository conversationRepository;
    private final UserService userService;
    private final ModerationAiService moderationAiService;
    private final RateLimitService rateLimitService;
    private final AccountSecurityService accountSecurityService;

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
        if (currentUser.getId().equals(reportedUser.getId())) {
            throw new BadRequestException("You cannot report your own account");
        }

        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        if (!containsMember(conversation, currentUser.getId())
                || !containsMember(conversation, reportedUser.getId())) {
            throw new UnauthorizedException("Both users must belong to the reported conversation");
        }

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

    @Override
    public java.util.List<UserReportResponse> getReviewQueue() {
        return userReportRepository
                .findByStatusInOrderByCreatedAtAsc(java.util.List.of("PENDING", "PENDING_REVIEW"))
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public UserReportResponse resolveReport(String reportId, ModerationDecisionRequest request) {
        User admin = userService.getCurrentAuthenticatedUser();
        UserReport report = userReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        if ("RESOLVED".equals(report.getStatus()) || "DISMISSED".equals(report.getStatus())) {
            throw new BadRequestException("Report has already been resolved");
        }

        ModerationDecision decision = request.getDecision();
        if (decision == ModerationDecision.BAN) {
            accountSecurityService.lockAndRevoke(report.getReportedUser());
            report.setStatus("RESOLVED");
        } else if (decision == ModerationDecision.WARN) {
            report.setStatus("RESOLVED");
        } else {
            report.setStatus("DISMISSED");
        }
        report.setFinalDecision(decision);
        report.setResolvedBy(admin);
        report.setResolvedAt(LocalDateTime.now());
        return mapToResponse(userReportRepository.save(report));
    }

    private boolean containsMember(Conversation conversation, String userId) {
        return conversation.getMembers() != null
                && conversation.getMembers().stream()
                .anyMatch(member -> member != null && userId.equals(member.getId()));
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
                .finalDecision(report.getFinalDecision() != null ? report.getFinalDecision().name() : null)
                .resolvedById(report.getResolvedBy() != null ? report.getResolvedBy().getId() : null)
                .resolvedAt(report.getResolvedAt())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
