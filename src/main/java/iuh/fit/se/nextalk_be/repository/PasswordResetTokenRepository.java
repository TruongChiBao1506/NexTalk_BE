package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.PasswordResetToken;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import iuh.fit.se.nextalk_be.entity.User;

@Repository
public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {
    Optional<PasswordResetToken> findByToken(String token);
    void deleteByUser(User user);
}
