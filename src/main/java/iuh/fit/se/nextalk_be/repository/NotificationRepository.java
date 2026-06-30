package iuh.fit.se.nextalk_be.repository;

import iuh.fit.se.nextalk_be.entity.Notification;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    List<Notification> findByRecipientIdAndIsReadFalse(String recipientId);

    long countByRecipientIdAndIsReadFalse(String recipientId);
}
