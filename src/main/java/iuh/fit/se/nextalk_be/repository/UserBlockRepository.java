package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.UserBlock;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBlockRepository extends MongoRepository<UserBlock, String> {
    Optional<UserBlock> findByBlockerIdAndBlockedId(String blockerId, String blockedId);

    boolean existsByBlockerIdAndBlockedId(String blockerId, String blockedId);

    List<UserBlock> findAllByBlockerIdOrBlockedId(String blockerId, String blockedId);

    default boolean existsBetweenUsers(String userId, String otherUserId) {
        return existsByBlockerIdAndBlockedId(userId, otherUserId)
                || existsByBlockerIdAndBlockedId(otherUserId, userId);
    }
}

