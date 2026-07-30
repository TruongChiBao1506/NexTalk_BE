package iuh.fit.se.nextalk_be.security;

import iuh.fit.se.nextalk_be.entity.User;
import iuh.fit.se.nextalk_be.exception.BadRequestException;
import iuh.fit.se.nextalk_be.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatPinSecurityServiceTest {
    @Test
    void failedAttemptAddsDelayAndLockedPinCannotBeRetriedImmediately() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UserRepository users = mock(UserRepository.class);
        User user = User.builder().chatPin("encoded").build();
        when(encoder.matches("0000", "encoded")).thenReturn(false);
        ChatPinSecurityService service = new ChatPinSecurityService(encoder, users);

        assertThrows(BadRequestException.class, () -> service.verifyOrThrow(user, "0000"));
        assertEquals(1, user.getChatPinFailedAttempts());
        assertTrue(user.getChatPinLockedUntil().isAfter(LocalDateTime.now()));
        assertThrows(BadRequestException.class, () -> service.verifyOrThrow(user, "0000"));
        verify(users, times(1)).save(user);
    }

    @Test
    void validPinClearsExistingFailures() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UserRepository users = mock(UserRepository.class);
        User user = User.builder().chatPin("encoded").chatPinFailedAttempts(3).build();
        when(encoder.matches("1234", "encoded")).thenReturn(true);
        ChatPinSecurityService service = new ChatPinSecurityService(encoder, users);

        service.verifyOrThrow(user, "1234");

        assertEquals(0, user.getChatPinFailedAttempts());
        assertNull(user.getChatPinLockedUntil());
    }
}
