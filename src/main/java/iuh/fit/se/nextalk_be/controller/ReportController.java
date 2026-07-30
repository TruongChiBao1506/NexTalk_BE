package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.request.CreateReportRequest;
import iuh.fit.se.nextalk_be.dto.request.ModerationDecisionRequest;
import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.UserReportResponse;
import iuh.fit.se.nextalk_be.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserReportResponse>> createReport(@Valid @RequestBody CreateReportRequest request) {
        UserReportResponse response = reportService.createReport(request);
        return ResponseEntity.ok(ApiResponse.<UserReportResponse>builder()
                .success(true)
                .message("Report created successfully. AI analysis is advisory and will not take automatic action.")
                .data(response)
                .build());
    }

    @GetMapping("/review-queue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<UserReportResponse>>> getReviewQueue() {
        return ResponseEntity.ok(ApiResponse.success(reportService.getReviewQueue()));
    }

    @PostMapping("/{reportId}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserReportResponse>> resolveReport(
            @PathVariable String reportId,
            @Valid @RequestBody ModerationDecisionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reportService.resolveReport(reportId, request),
                "Moderation decision applied"));
    }
}
