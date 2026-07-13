package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.Message;
import iuh.fit.se.nextalk_be.entity.MessageType;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    Page<Message> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);

    @Query(value = "{'$and':[{'$or':[{'conversationId':?0},{'conversation':?0}]},{'deletedByUsers':{'$ne':?1}}]}", sort = "{'createdAt':-1}")
    Slice<Message> findVisibleConversationMessages(String conversationId, String userId, Pageable pageable);

    List<Message> findByConversationIdIsNull(Pageable pageable);

    List<Message> findAllByConversationId(String conversationId);

    @Aggregation(pipeline = {
            "{'$match': {'conversationId': {'$in': ?0}, 'deletedByUsers': {'$ne': ?1}}}",
            "{'$sort': {'createdAt': -1}}",
            "{'$group': {'_id': '$conversationId', 'message': {'$first': '$$ROOT'}}}",
            "{'$replaceRoot': {'newRoot': '$message'}}"
    })
    List<Message> findLatestVisibleByConversationIds(List<String> conversationIds, String userId);

    List<Message> findByConversationIdAndIsPinnedTrue(String conversationId);

    List<Message> findByConversationIdAndContentContainingIgnoreCaseAndMessageTypeAndIsRecalledFalse(String conversationId, String content, MessageType messageType);

    List<Message> findByConversationIdInAndContentContainingIgnoreCaseAndMessageTypeAndIsRecalledFalse(List<String> conversationIds, String content, MessageType messageType);
}
