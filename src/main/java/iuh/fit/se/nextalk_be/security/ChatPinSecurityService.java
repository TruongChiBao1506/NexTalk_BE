package iuh.fit.se.nextalk_be.security;

import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ChatPinSecurityService {
    private static final int MAX_ATTEMPTS = 5;

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public void verifyOrThrow(User user, String pin) {
        LocalDateTime now = LocalDateTime.now();
        if (user.getChatPinLockedUntil() != null && user.getChatPinLockedUntil().isAfter(now)) {
            throw new BadRequestException("Chat PIN is temporarily locked. Please try again later.");
        }
        if (user.getChatPin() != null && passwordEncoder.matches(pin, user.getChatPin())) {
            clearFailures(user);
            userRepository.save(user);
            return;
        }

        int attempts = user.getChatPinFailedAttempts() + 1;
        user.setChatPinFailedAttempts(attempts);
        user.setChatPinLockedUntil(attempts >= MAX_ATTEMPTS
                ? now.plusMinutes(15)
                : now.plusSeconds(Math.min(30, 1L << attempts)));
        userRepository.save(user);
        throw new BadRequestException("Chat PIN is invalid or temporarily locked.");
    }

    public void clearFailures(User user) {
        user.setChatPinFailedAttempts(0);
        user.setChatPinLockedUntil(null);
    }
}
