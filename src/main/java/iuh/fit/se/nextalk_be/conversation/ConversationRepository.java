package iuh.fit.se.nextalk_be.conversation;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, UUID> {

    @Query("{ 'type': 'PRIVATE', 'members': { '$all': [ ?0, ?1 ] } }")
    Optional<Conversation> findPrivateConversationBetweenUsers(UUID user1Id, UUID user2Id);

    @Query(value = "{ 'members': ?0 }", sort = "{ 'updatedAt': -1 }")
    List<Conversation> findAllByUserIdOrderByUpdatedAtDesc(UUID userId);
}
