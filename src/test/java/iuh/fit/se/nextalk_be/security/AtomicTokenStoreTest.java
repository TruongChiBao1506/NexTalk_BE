package iuh.fit.se.nextalk_be.security;

import iuh.fit.se.nextalk_be.entity.RefreshToken;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AtomicTokenStoreTest {
    @Test
    void onlySuccessfulCompareAndSwapReturnsRotatedSession() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        RefreshToken rotated = RefreshToken.builder().token("new-digest").build();
        when(mongo.findAndModify(
                any(Query.class),
                any(UpdateDefinition.class),
                any(FindAndModifyOptions.class),
                eq(RefreshToken.class)))
                .thenReturn(rotated)
                .thenReturn(null);
        AtomicTokenStore store = new AtomicTokenStore(mongo);
        LocalDateTime now = LocalDateTime.now();

        assertTrue(store.rotateRefreshToken(
                "session", "old", "new", now.plusDays(7), now, "ip", "agent").isPresent());
        assertTrue(store.rotateRefreshToken(
                "session", "old", "other", now.plusDays(7), now, "ip", "agent").isEmpty());
    }
}
