package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.ConversationTag;
import iuh.fit.se.nextalk_be.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationTagRepository extends MongoRepository<ConversationTag, String> {
    List<ConversationTag> findByUserOrderByPositionAscCreatedAtAsc(User user);
    Optional<ConversationTag> findByIdAndUser(String id, User user);
    Optional<ConversationTag> findByUserAndNameIgnoreCase(User user, String name);
    boolean existsByUserAndNameIgnoreCase(User user, String name);
}
