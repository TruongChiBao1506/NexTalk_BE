package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.EmailVerification;
import iuh.fit.se.nextalk_be.entity.User;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailVerificationRepository extends MongoRepository<EmailVerification, String> {

    Optional<EmailVerification> findByToken(String token);

    Optional<EmailVerification> findByUser(User user);
}
