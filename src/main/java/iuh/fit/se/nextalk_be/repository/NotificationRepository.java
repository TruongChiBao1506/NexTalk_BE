package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.Notification;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    Optional<Notification> findByPushIdempotencyKey(String pushIdempotencyKey);

    long countByDeliveryStatus(iuh.fit.se.nextalk_be.entity.NotificationDeliveryStatus deliveryStatus);

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    List<Notification> findByRecipientIdAndIsReadFalse(String recipientId);

    long countByRecipientIdAndIsReadFalse(String recipientId);

    @Query(value = "{'$or': [{'recipient': ?0}, {'recipient.$id': ?0}, {'recipient._id': ?0}, {'recipient': ?1}, {'recipient.$id': ?1}, {'recipient._id': ?1}]}", sort = "{'createdAt': -1}")
    List<Notification> findTop50ByRecipientUser(String recipientId, ObjectId recipientObjectId);

    @Query(value = "{'isRead': false, '$or': [{'recipient': ?0}, {'recipient.$id': ?0}, {'recipient._id': ?0}, {'recipient': ?1}, {'recipient.$id': ?1}, {'recipient._id': ?1}]}", count = true)
    long countUnreadByRecipientUser(String recipientId, ObjectId recipientObjectId);

    @Query(value = "{'actionStatus': {'$exists': true, '$ne': null}, '$or': [{'recipient': ?0}, {'recipient.$id': ?0}, {'recipient._id': ?0}, {'recipient': ?1}, {'recipient.$id': ?1}, {'recipient._id': ?1}]}", sort = "{'createdAt': -1}")
    List<Notification> findActionItemsByRecipientUser(String recipientId, ObjectId recipientObjectId);
}
