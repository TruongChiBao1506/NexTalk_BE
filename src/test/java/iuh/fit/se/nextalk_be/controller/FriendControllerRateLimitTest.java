package iuh.fit.se.nextalk_be.controller;

import iuh.fit.se.nextalk_be.dto.request.FriendshipRequest;
import iuh.fit.se.nextalk_be.security.RateLimitService;
import iuh.fit.se.nextalk_be.service.FriendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendControllerRateLimitTest {

    @Mock private FriendService friendService;
    @Mock private RateLimitService rateLimitService;

    private FriendController controller;

    @BeforeEach
    void setUp() {
        controller = new FriendController(friendService, rateLimitService);
        when(rateLimitService.currentUserIdentity()).thenReturn("current-user");
    }

    @Test
    void limitsFriendRequestCreation() {
        FriendshipRequest request = new FriendshipRequest();
        request.setReceiverId("candidate-user");

        controller.sendFriendRequest(request);

        verify(rateLimitService).check(
                "friend-request:send",
                "current-user",
                30,
                Duration.ofHours(1));
        verify(friendService).sendFriendRequest("candidate-user");
    }

    @Test
    void limitsSuggestionReadsAndDismissals() {
        controller.getFriendSuggestions(20);
        controller.dismissFriendSuggestion("candidate-user");

        verify(rateLimitService).check(
                "friend-suggestion:list",
                "current-user",
                60,
                Duration.ofMinutes(1));
        verify(rateLimitService).check(
                "friend-suggestion:dismiss",
                "current-user",
                60,
                Duration.ofMinutes(1));
        verify(friendService).getFriendSuggestions(20);
        verify(friendService).dismissFriendSuggestion("candidate-user");
    }
}
