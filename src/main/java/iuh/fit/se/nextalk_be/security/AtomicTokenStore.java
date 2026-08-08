package iuh.fit.se.nextalk_be.security;

import iuh.fit.se.nextalk_be.entity.EmailVerification;
import iuh.fit.se.nextalk_be.entity.PasswordResetToken;
import iuh.fit.se.nextalk_be.entity.QrLoginSession;
import iuh.fit.se.nextalk_be.entity.QrLoginStatus;
import iuh.fit.se.nextalk_be.entity.RefreshToken;
import iuh.fit.se.nextalk_be.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AtomicTokenStore {
    private final MongoTemplate mongoTemplate;

    public Optional<EmailVerification> consumeEmailVerification(String tokenDigest, LocalDateTime now) {
        Query query = Query.query(Criteria.where("token").is(tokenDigest)
                .and("verified").is(false)
                .and("expiresAt").gt(now));
        Update update = new Update().set("verified", true);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(false), EmailVerification.class));
    }

    public Optional<PasswordResetToken> consumePasswordReset(String tokenDigest, LocalDateTime now) {
        Query query = Query.query(Criteria.where("token").is(tokenDigest)
                .and("used").is(false)
                .and("expiresAt").gt(now));
        Update update = new Update().set("used", true);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(false), PasswordResetToken.class));
    }

    public Optional<QrLoginSession> confirmPendingQr(
            String qrTokenDigest, User user, LocalDateTime now) {
        Query query = Query.query(Criteria.where("qrToken").is(qrTokenDigest)
                .and("status").is(QrLoginStatus.PENDING)
                .and("expiresAt").gt(now));
        Update update = new Update()
                .set("status", QrLoginStatus.CONFIRMED)
                .set("user", user)
                .set("confirmedAt", now);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), QrLoginSession.class));
    }

    public Optional<QrLoginSession> consumeConfirmedQr(String sessionDigest, LocalDateTime now) {
        Query query = Query.query(Criteria.where("sessionId").is(sessionDigest)
                .and("status").is(QrLoginStatus.CONFIRMED)
                .and("expiresAt").gt(now));
        Update update = new Update()
                .set("status", QrLoginStatus.CONSUMED)
                .set("consumedAt", now);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(false), QrLoginSession.class));
    }

    public Optional<RefreshToken> rotateRefreshToken(
            String sessionId,
            String expectedDigest,
            String replacementDigest,
            LocalDateTime replacementExpiry,
            LocalDateTime now,
            String ipAddress,
            String userAgent) {
        Query query = Query.query(Criteria.where("_id").is(sessionId)
                .and("token").is(expectedDigest)
                .and("expiresAt").gt(now));
        Update update = new Update()
                .set("previousTokenDigest", expectedDigest)
                .set("token", replacementDigest)
                .set("expiresAt", replacementExpiry)
                .set("lastUsedAt", now)
                .set("ipAddress", ipAddress)
                .set("userAgent", userAgent)
                .addToSet("usedTokenDigests", expectedDigest);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), RefreshToken.class));
    }
}
