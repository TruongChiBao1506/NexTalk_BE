package iuh.fit.se.nextalk_be.service;

import iuh.fit.se.nextalk_be.dto.request.CreateReportRequest;
import iuh.fit.se.nextalk_be.dto.response.UserReportResponse;

public interface ReportService {
    UserReportResponse createReport(CreateReportRequest request);
}
