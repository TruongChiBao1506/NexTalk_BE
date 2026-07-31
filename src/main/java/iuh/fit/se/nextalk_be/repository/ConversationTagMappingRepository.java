package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.ConversationTag;
import iuh.fit.se.nextalk_be.entity.ConversationTagMapping;
import iuh.fit.se.nextalk_be.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationTagMappingRepository extends MongoRepository<ConversationTagMapping, String> {
    List<ConversationTagMapping> findByUser(User user);
    List<ConversationTagMapping> findByUserAndTag(User user, ConversationTag tag);
    List<ConversationTagMapping> findByUserAndTargetId(User user, String targetId);
    Optional<ConversationTagMapping> findByUserAndTagAndTargetId(User user, ConversationTag tag, String targetId);
    void deleteByTag(ConversationTag tag);
    void deleteByUserAndTagAndTargetId(User user, ConversationTag tag, String targetId);
}
