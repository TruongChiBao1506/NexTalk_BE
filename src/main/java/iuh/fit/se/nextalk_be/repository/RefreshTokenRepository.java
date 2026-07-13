package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.RefreshToken;
import iuh.fit.se.nextalk_be.entity.User;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {

    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByIdAndUserId(String id, String userId);

    boolean existsByIdAndUserId(String id, String userId);

    List<RefreshToken> findByUserIdOrderByCreatedAtDesc(String userId);

    void deleteByToken(String token);

    void deleteByUser(User user);
}
