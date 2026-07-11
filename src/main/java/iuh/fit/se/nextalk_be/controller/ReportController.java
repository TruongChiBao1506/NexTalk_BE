package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.request.CreateReportRequest;
import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.dto.response.UserReportResponse;
import iuh.fit.se.nextalk_be.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
                .message("Report created successfully and AI moderation is processing")
                .data(response)
                .build());
    }
}
