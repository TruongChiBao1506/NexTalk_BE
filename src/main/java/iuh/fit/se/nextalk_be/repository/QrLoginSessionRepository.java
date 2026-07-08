package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.QrLoginSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QrLoginSessionRepository extends MongoRepository<QrLoginSession, String> {
    Optional<QrLoginSession> findBySessionId(String sessionId);

    Optional<QrLoginSession> findByQrToken(String qrToken);
}
