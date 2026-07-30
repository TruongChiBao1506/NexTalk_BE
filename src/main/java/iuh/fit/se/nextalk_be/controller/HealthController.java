package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.service.DependencyHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final DependencyHealthService dependencyHealthService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "status", "UP",
                "service", "NexTalk_BE",
                "timestamp", Instant.now().toString()
        ), "Server is awake"));
    }

    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "timestamp", Instant.now().toString()
        ));
    }

    @GetMapping("/readiness")
    public ResponseEntity<ApiResponse<Map<String, Object>>> readiness() {
        DependencyHealthService.ReadinessSnapshot snapshot = dependencyHealthService.readiness();
        Map<String, Object> data = Map.of(
                "status", snapshot.ready() ? "UP" : "DOWN",
                "service", "NexTalk_BE",
                "dependencies", snapshot.dependencies(),
                "timestamp", Instant.now().toString()
        );
        ApiResponse<Map<String, Object>> response = ApiResponse.<Map<String, Object>>builder()
                .success(snapshot.ready())
                .message(snapshot.ready() ? "Service is ready" : "A required dependency is unavailable")
                .data(data)
                .build();
        return ResponseEntity
                .status(snapshot.ready() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }
}
