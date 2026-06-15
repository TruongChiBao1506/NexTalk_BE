package iuh.fit.se.nextalk_be.fcm;

import iuh.fit.se.nextalk_be.common.ApiResponse;
import iuh.fit.se.nextalk_be.user.User;
import iuh.fit.se.nextalk_be.user.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

@RestController
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
public class FCMController {

    private final UserRepository userRepository;

    @Data
    public static class FCMTokenRequest {
        private String token;
    }

    @PostMapping("/token")
    public ResponseEntity<ApiResponse<Void>> updateToken(@AuthenticationPrincipal User user, @RequestBody FCMTokenRequest request) {
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
        }

        return ResponseEntity.ok(ApiResponse.success(null, "Token updated successfully"));
    }
}
