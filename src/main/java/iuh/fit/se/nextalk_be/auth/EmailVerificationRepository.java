package iuh.fit.se.nextalk_be.auth;

import iuh.fit.se.nextalk_be.user.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationRepository extends MongoRepository<EmailVerification, UUID> {

    Optional<EmailVerification> findByToken(String token);

    Optional<EmailVerification> findByUser(User user);
}
