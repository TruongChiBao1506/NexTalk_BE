package iuh.fit.se.nextalk_be.conversation;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import org.bson.types.ObjectId;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    @Query("{ 'type': 'PRIVATE', 'members': { '$all': [ ?0, ?1 ] } }")
    Optional<Conversation> findPrivateConversationBetweenUsers(ObjectId user1Id, ObjectId user2Id);

    List<Conversation> findAllByMembersIdOrderByUpdatedAtDesc(String userId);

    List<Conversation> findAllByHiddenByUsersContaining(String userId);
}
