package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.response.ApiResponse;
import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import iuh.fit.se.nextalk_be.repository.RefreshTokenRepository;


import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
public class FCMController {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Data
    public static class FCMTokenRequest {
        private String token;
    }

    @PostMapping("/token")
    public ResponseEntity<ApiResponse<Void>> updateToken(@AuthenticationPrincipal User user, @RequestBody FCMTokenRequest request, HttpServletRequest httpRequest) {
        if (user == null || request.getToken() == null || request.getToken().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid token"));
        }

        User currentUser = userRepository.findById(user.getId()).orElse(null);
        if (currentUser != null) {
            if (currentUser.getFcmTokens() == null) {
                currentUser.setFcmTokens(new ArrayList<>());
            }
            if (!currentUser.getFcmTokens().contains(request.getToken())) {
                currentUser.getFcmTokens().add(request.getToken());
                userRepository.save(currentUser);
            }
            Object sessionId = httpRequest.getAttribute("loginSessionId");
            if (sessionId instanceof String id) {
                refreshTokenRepository.findByIdAndUserId(id, currentUser.getId()).ifPresent(session -> {
                    session.setFcmToken(request.getToken());
                    refreshTokenRepository.save(session);
                });
            }
        }

        return ResponseEntity.ok(ApiResponse.success(null, "Token updated successfully"));
    }

    @DeleteMapping("/token")
    public ResponseEntity<ApiResponse<Void>> deleteToken(@AuthenticationPrincipal User user, @RequestBody FCMTokenRequest request, HttpServletRequest httpRequest) {
        if (user == null || request.getToken() == null || request.getToken().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid token"));
        }

        User currentUser = userRepository.findById(user.getId()).orElse(null);
        if (currentUser != null && currentUser.getFcmTokens() != null) {
            currentUser.getFcmTokens().remove(request.getToken());
            userRepository.save(currentUser);
            Object sessionId = httpRequest.getAttribute("loginSessionId");
            if (sessionId instanceof String id) {
                refreshTokenRepository.findByIdAndUserId(id, currentUser.getId()).ifPresent(session -> {
                    session.setFcmToken(null);
                    refreshTokenRepository.save(session);
                });
            }
        }

        return ResponseEntity.ok(ApiResponse.success(null, "Token deleted successfully"));
    }
}
