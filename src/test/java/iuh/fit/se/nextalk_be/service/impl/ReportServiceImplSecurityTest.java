package iuh.fit.se.nextalk_be.service.impl;

import iuh.fit.se.nextalk_be.dto.request.CreateReportRequest;
import iuh.fit.se.nextalk_be.dto.request.ModerationDecisionRequest;
import iuh.fit.se.nextalk_be.entity.Conversation;
import iuh.fit.se.nextalk_be.entity.ModerationDecision;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.entity.UserReport;
import iuh.fit.se.nextalk_be.exception.UnauthorizedException;
import iuh.fit.se.nextalk_be.repository.ConversationRepository;
import iuh.fit.se.nextalk_be.repository.UserReportRepository;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.service.AccountSecurityService;
import iuh.fit.se.nextalk_be.service.ModerationAiService;
import iuh.fit.se.nextalk_be.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplSecurityTest {
    @Mock private UserReportRepository userReportRepository;
    @Mock private UserRepository userRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private UserService userService;
    @Mock private ModerationAiService moderationAiService;
    @Mock private RateLimitService rateLimitService;
    @Mock private AccountSecurityService accountSecurityService;

    private ReportServiceImpl service;
    private User reporter;
    private User reported;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(
                userReportRepository,
                userRepository,
                conversationRepository,
                userService,
                moderationAiService,
                rateLimitService,
                accountSecurityService);
        reporter = user("reporter-1", "reporter");
        reported = user("reported-1", "reported");
    }

    @Test
    void reportIsRejectedUnlessBothUsersBelongToConversation() {
        CreateReportRequest request = reportRequest();
        Conversation conversation = Conversation.builder().members(Set.of(reporter)).build();
        conversation.setId(request.getConversationId());
        when(userService.getCurrentAuthenticatedUser()).thenReturn(reporter);
        when(userRepository.findById(reported.getId())).thenReturn(Optional.of(reported));
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        assertThrows(UnauthorizedException.class, () -> service.createReport(request));
        verify(userReportRepository, never()).save(any());
        verify(moderationAiService, never()).evaluateReportAsync(any());
    }

    @Test
    void validConversationReportIsSavedForAdvisoryAnalysis() {
        CreateReportRequest request = reportRequest();
        Conversation conversation = Conversation.builder().members(Set.of(reporter, reported)).build();
        conversation.setId(request.getConversationId());
        when(userService.getCurrentAuthenticatedUser()).thenReturn(reporter);
        when(userRepository.findById(reported.getId())).thenReturn(Optional.of(reported));
        when(conversationRepository.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        when(userReportRepository.save(any(UserReport.class))).thenAnswer(invocation -> {
            UserReport report = invocation.getArgument(0);
            report.setId("report-1");
            return report;
        });

        service.createReport(request);

        verify(moderationAiService).evaluateReportAsync("report-1");
    }

    @Test
    void onlyManualBanDecisionInvokesAccountLocking() {
        User admin = user("admin-1", "admin");
        UserReport report = UserReport.builder()
                .reporter(reporter)
                .reportedUser(reported)
                .status("PENDING_REVIEW")
                .build();
        report.setId("report-1");
        ModerationDecisionRequest request = new ModerationDecisionRequest();
        request.setDecision(ModerationDecision.BAN);

        when(userService.getCurrentAuthenticatedUser()).thenReturn(admin);
        when(userReportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(userReportRepository.save(report)).thenReturn(report);

        var response = service.resolveReport(report.getId(), request);

        assertEquals("BAN", response.getFinalDecision());
        verify(accountSecurityService).lockAndRevoke(reported);
    }

    private CreateReportRequest reportRequest() {
        return CreateReportRequest.builder()
                .reportedUserId(reported.getId())
                .conversationId("conversation-1")
                .reason("Abuse")
                .description("Evidence for review")
                .build();
    }

    private User user(String id, String username) {
        User user = User.builder().email(username + "@example.test").username(username).build();
        user.setId(id);
        return user;
    }
}
