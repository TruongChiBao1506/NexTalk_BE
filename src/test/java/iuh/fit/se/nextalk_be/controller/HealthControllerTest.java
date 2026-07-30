package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.service.DependencyHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    @Test
    void readinessReturnsServiceUnavailableWhenARequiredDependencyIsDown() {
        DependencyHealthService health = mock(DependencyHealthService.class);
        when(health.readiness()).thenReturn(new DependencyHealthService.ReadinessSnapshot(
                false,
                Map.of("mongodb", "DOWN", "redis", "UP")
        ));

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                new HealthController(health).readiness();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("DOWN", response.getBody().getData().get("status"));
    }
}
